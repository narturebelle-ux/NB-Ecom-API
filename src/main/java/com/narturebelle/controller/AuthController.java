package com.narturebelle.controller;

import com.narturebelle.dto.UserProfileResponse;
import com.narturebelle.model.Role;
import com.narturebelle.model.User;
import com.narturebelle.repository.UserRepository;
import com.narturebelle.security.AuditService;
import com.narturebelle.security.AuthorizationService;
import com.narturebelle.security.FirebaseUserPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final AuthorizationService authorizationService;
    private final AuditService auditService;

    public AuthController(UserRepository userRepository, AuthorizationService authorizationService,
                           AuditService auditService) {
        this.userRepository = userRepository;
        this.authorizationService = authorizationService;
        this.auditService = auditService;
    }

    /**
     * Called by the UI right after any sign-in. Requires Authorization: Bearer <Firebase ID token>
     * (verified by FirebaseAuthenticationFilter before this method runs). Creates the user on first
     * login, or updates email/phone/verification status from the token on subsequent logins.
     */
    @PostMapping("/firebase")
    public ResponseEntity<UserProfileResponse> syncFirebaseUser() {
        FirebaseUserPrincipal principal = authorizationService.currentPrincipal();

        Optional<User> existing = userRepository.findByFirebaseUid(principal.firebaseUid());
        boolean isNewUser = existing.isEmpty();
        User user = existing.orElseGet(() -> {
            User newUser = new User();
            newUser.setFirebaseUid(principal.firebaseUid());
            newUser.setRoles(Set.of(Role.MOTHER));
            return newUser;
        });

        // Covers both brand-new users and legacy existing ones with no name on file (0.7 backfill).
        // Never touches a user who already has a name - manual edits via PUT/PATCH /me are safe.
        if (isBlank(user.getFirstName()) && isBlank(user.getLastName())) {
            String[] derivedName = deriveName(principal);
            user.setFirstName(derivedName[0]);
            user.setLastName(derivedName[1]);
        }

        user.setEmail(principal.email());
        user.setEmailVerified(principal.emailVerified());
        if (principal.phone() != null) {
            user.setPhone(principal.phone());
            user.setPhoneVerified(true);
        }

        User saved = userRepository.save(user);
        if (isNewUser) {
            auditService.record("USER_CREATED", "User", String.valueOf(saved.getId()), null);
        }
        return ResponseEntity.ok(UserProfileResponse.from(saved));
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
