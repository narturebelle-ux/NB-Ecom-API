package com.narturebelle.controller;

import com.narturebelle.dto.UserProfileResponse;
import com.narturebelle.exception.AccountConflictException;
import com.narturebelle.model.Role;
import com.narturebelle.model.User;
import com.narturebelle.repository.UserRepository;
import com.narturebelle.security.AuditService;
import com.narturebelle.security.AuthorizationService;
import com.narturebelle.security.FirebaseUserPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);
    private static final int MAX_SYNC_ATTEMPTS = 3;

    private final UserRepository userRepository;
    private final AuthorizationService authorizationService;
    private final AuditService auditService;
    private final TransactionTemplate transactionTemplate;

    public AuthController(UserRepository userRepository, AuthorizationService authorizationService,
                           AuditService auditService, TransactionTemplate transactionTemplate) {
        this.userRepository = userRepository;
        this.authorizationService = authorizationService;
        this.auditService = auditService;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * Called by the UI right after any sign-in. Requires Authorization: Bearer <Firebase ID token>
     * (verified by FirebaseAuthenticationFilter before this method runs). Creates the user on first
     * login, or updates email/phone/verification status from the token on subsequent logins. A new
     * UID with an email we already hold is relinked only when both sides are verified, otherwise 409.
     */
    @PostMapping("/firebase")
    public ResponseEntity<?> syncFirebaseUser() {
        FirebaseUserPrincipal principal = authorizationService.currentPrincipal();

        // A simultaneous sign-in can take the UID, email or phone between our lookups and our save. Postgres
        // has then aborted the transaction, so each attempt gets a new one, which sees what the other saved.
        // The exception isn't logged: its database message contains the email or phone.
        for (int attempt = 1; attempt <= MAX_SYNC_ATTEMPTS; attempt++) {
            try {
                User saved = transactionTemplate.execute(status -> syncUser(principal));
                return ResponseEntity.ok(UserProfileResponse.from(saved));
            } catch (DataIntegrityViolationException e) {
                log.info("Sign-in lost a race for a unique value (attempt {} of {})", attempt, MAX_SYNC_ATTEMPTS);
            }
        }
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("code", "SERVICE_UNAVAILABLE", "message", "Please try again in a moment."));
    }

    private User syncUser(FirebaseUserPrincipal principal) {
        Optional<User> existing = userRepository.findByFirebaseUid(principal.firebaseUid());
        boolean isNewUser = false;
        String relinkedFromUid = null;
        boolean relinked = false;
        User user;
        if (existing.isPresent()) {
            user = existing.get();
        } else {
            Optional<User> sameEmail = findRelinkableUser(principal);
            if (sameEmail.isPresent()) {
                user = sameEmail.get();
                relinked = true;
                relinkedFromUid = user.getFirebaseUid();
                user.setFirebaseUid(principal.firebaseUid());
            } else {
                requirePhoneNotOnAnotherAccount(principal);
                isNewUser = true;
                user = new User();
                user.setFirebaseUid(principal.firebaseUid());
                user.setRoles(Set.of(Role.MOTHER));
            }
        }

        // Covers both brand-new users and legacy existing ones with no name on file (0.7 backfill).
        // Never touches a user who already has a name - manual edits via PUT/PATCH /me are safe.
        if (isBlank(user.getFirstName()) && isBlank(user.getLastName())) {
            String[] derivedName = deriveName(principal);
            user.setFirstName(derivedName[0]);
            user.setLastName(derivedName[1]);
        }

        applyEmail(user, principal);
        applyPhone(user, principal);

        // Flush now, so a refused row surfaces here and not somewhere we can't retry from.
        User saved = userRepository.saveAndFlush(user);
        if (isNewUser) {
            auditService.record("USER_CREATED", "User", String.valueOf(saved.getId()), null);
        }
        if (relinked) {
            // Firebase UIDs only: never the email or the token.
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("newFirebaseUid", principal.firebaseUid());
            if (relinkedFromUid != null) {
                metadata.put("previousFirebaseUid", relinkedFromUid);
            }
            auditService.record("USER_FIREBASE_RELINKED", "User", String.valueOf(saved.getId()),
                    "Same verified email on the token and the account", metadata);
        }
        return saved;
    }

    /**
     * The user a new UID may take over because of its email, if any. A new Firebase account can claim any
     * email unverified, so both sides must be verified: this is what keeps someone else out of a mother's
     * health data. Compares ignoring case, because the unique rule doesn't, so two rows can differ by case.
     */
    private Optional<User> findRelinkableUser(FirebaseUserPrincipal principal) {
        if (isBlank(principal.email())) {
            return Optional.empty();
        }
        List<User> matches = userRepository.findAllByEmailIgnoreCase(principal.email());
        if (matches.isEmpty()) {
            return Optional.empty();
        }
        matches.forEach(match -> retryIfSavedByThisSignIn(match, principal));
        if (matches.size() > 1) {
            log.warn("DUPLICATE ACCOUNTS need a manual review: users {} have the same email apart from its case",
                    matches.stream().map(User::getId).toList());
            throw emailConflict();
        }
        User match = matches.get(0);
        if (!principal.emailVerified() || !Boolean.TRUE.equals(match.getEmailVerified())) {
            log.warn("Refused to relink user {}: email verified on token={}, on account={}",
                    match.getId(), principal.emailVerified(), match.getEmailVerified());
            throw emailConflict();
        }
        return Optional.of(match);
    }

    /** We never move an account by phone alone, and a new account can't share the phone's unique rule. */
    private void requirePhoneNotOnAnotherAccount(FirebaseUserPrincipal principal) {
        if (principal.phone() == null) {
            return;
        }
        userRepository.findByPhone(principal.phone()).ifPresent(other -> {
            retryIfSavedByThisSignIn(other, principal);
            log.warn("Refused a new account: its phone is on user {}", other.getId());
            throw new AccountConflictException("ACCOUNT_PHONE_CONFLICT",
                    "This phone number is already linked to another account. Please sign in the way you did before, "
                            + "or contact hello@narturebelle.com.");
        });
    }

    /** A row with our own UID was saved by a copy of this sign-in after our UID lookup: the retry will find it by UID. */
    private void retryIfSavedByThisSignIn(User row, FirebaseUserPrincipal principal) {
        if (principal.firebaseUid().equals(row.getFirebaseUid())) {
            throw new DataIntegrityViolationException("Saved by a simultaneous copy of this sign-in");
        }
    }

    /**
     * Keeps the stored email and its verified status if another user holds the token's email: the token
     * vouches for its own email, so its status must not be written onto a different address. A new user
     * skips this: if the email was just taken, the unique rule refuses the row and the retry decides.
     */
    private void applyEmail(User user, FirebaseUserPrincipal principal) {
        String email = principal.email();
        if (user.getId() != null && email != null && !email.equals(user.getEmail()) && isEmailOnAnotherUser(email, user)) {
            log.warn("Kept user {}'s stored email: the token's email is on another account", user.getId());
            return;
        }
        user.setEmail(email);
        user.setEmailVerified(principal.emailVerified());
    }

    private boolean isEmailOnAnotherUser(String email, User user) {
        return userRepository.findAllByEmailIgnoreCase(email).stream()
                .anyMatch(other -> !other.getId().equals(user.getId()));
    }

    private void applyPhone(User user, FirebaseUserPrincipal principal) {
        String phone = principal.phone();
        if (phone == null) {
            return;
        }
        if (user.getId() != null && !phone.equals(user.getPhone())) {
            Optional<User> holder = userRepository.findByPhone(phone);
            if (holder.isPresent() && !holder.get().getId().equals(user.getId())) {
                log.warn("Kept user {}'s stored phone: the token's phone is on user {}", user.getId(), holder.get().getId());
                return;
            }
        }
        user.setPhone(phone);
        user.setPhoneVerified(true);
    }

    private static AccountConflictException emailConflict() {
        return new AccountConflictException("ACCOUNT_EMAIL_CONFLICT",
                "We couldn't confirm this sign-in belongs to the existing account for this email. Please verify your "
                        + "email or sign in the way you did before. If this continues, contact hello@narturebelle.com.");
    }

    /**
     * Only called when creating a brand-new user — never on later logins, so a user's manual edits
     * via PUT /users/me are never clobbered. Google sign-in gives a real display name; everything
     * else falls back to guessing from the email's local-part; phone-only has nothing to derive from.
     */
    private static String[] deriveName(FirebaseUserPrincipal principal) {
        if (principal.name() != null && !principal.name().isBlank()) {
            String[] parts = principal.name().trim().split("\\s+", 2);
            return new String[]{parts[0], parts.length > 1 ? parts[1] : ""};
        }
        if (principal.email() != null && !principal.email().isBlank()) {
            String localPart = principal.email().split("@", 2)[0];
            List<String> segments = Arrays.stream(localPart.split("[._\\-0-9]+"))
                    .filter(s -> !s.isBlank())
                    .map(AuthController::titleCase)
                    .toList();
            if (segments.isEmpty()) {
                return new String[]{null, null};
            }
            String first = segments.get(0);
            String last = segments.size() > 1 ? String.join(" ", segments.subList(1, segments.size())) : "";
            return new String[]{first, last};
        }
        return new String[]{null, null};
    }

    private static String titleCase(String s) {
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
