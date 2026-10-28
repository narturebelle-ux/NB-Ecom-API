package com.narturebelle.controller;

import com.narturebelle.dto.UpdateProfileRequest;
import com.narturebelle.dto.UserProfileResponse;
import com.narturebelle.model.PregnancyStage;
import com.narturebelle.model.User;
import com.narturebelle.repository.UserRepository;
import com.narturebelle.security.AuthorizationService;
import com.narturebelle.security.FirebaseUserPrincipal;
import com.narturebelle.security.RequireVerified;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserRepository userRepository;
    private final AuthorizationService authorizationService;

    public UserController(UserRepository userRepository, AuthorizationService authorizationService) {
        this.userRepository = userRepository;
        this.authorizationService = authorizationService;
    }

    /** GET /api/v1/users/me — the logged-in user's own profile. */
    @GetMapping("/me")
    public ResponseEntity<?> getMyProfile() {
        User user = loadSelfOrNotFound();
        if (user == null) {
            return profileNotCreatedYet();
        }
        return ResponseEntity.ok(UserProfileResponse.from(user));
    }

    /** PUT /api/v1/users/me — update the logged-in user's own profile. Requires a verified user. */
    @RequireVerified
    @PutMapping("/me")
    public ResponseEntity<?> updateMyProfile(@Valid @RequestBody UpdateProfileRequest req) {
        User user = loadSelfOrNotFound();
        if (user == null) {
            return profileNotCreatedYet();
        }

        if (req.getFirstName() != null && !req.getFirstName().isBlank()) {
            user.setFirstName(req.getFirstName().trim());
        }
        if (req.getLastName() != null) {
            user.setLastName(req.getLastName().trim());
        }
        if (req.getPhone() != null && !req.getPhone().isBlank()) {
            String newPhone = req.getPhone().trim();
            Optional<User> existingPhone = userRepository.findByPhone(newPhone);
            if (existingPhone.isPresent() && !existingPhone.get().getId().equals(user.getId())) {
                return ResponseEntity.badRequest()
                        .body(Map.of("code", "PHONE_IN_USE", "message", "Phone number already in use by another account."));
            }
            if (!newPhone.equals(user.getPhone())) {
                // Only Firebase phone verification (via /auth/firebase) sets this true again.
                user.setPhoneVerified(false);
            }
            user.setPhone(newPhone);
        }
        if (req.getStage() != null && !req.getStage().isBlank()) {
            try {
                user.setStage(PregnancyStage.valueOf(req.getStage().toUpperCase()));
            } catch (IllegalArgumentException ignored) {
                // keep existing stage if invalid value sent
            }
        }
        if (req.getDueDate() != null) {
            user.setDueDate(req.getDueDate());
        }

        User saved = userRepository.save(user);
        return ResponseEntity.ok(UserProfileResponse.from(saved));
    }

    /** GET /api/v1/users/{id} — self or ADMIN only. */
    @GetMapping("/{id}")
    public ResponseEntity<?> getProfile(@PathVariable Long id) {
        authorizationService.requireSelfOrAdmin(id);
        Optional<User> opt = userRepository.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(UserProfileResponse.from(opt.get()));
    }

    private User loadSelfOrNotFound() {
        FirebaseUserPrincipal principal = authorizationService.currentPrincipal();
        if (principal.userId() == null) {
            return null;
        }
        return userRepository.findById(principal.userId()).orElse(null);
    }

    private ResponseEntity<Map<String, String>> profileNotCreatedYet() {
        return ResponseEntity.status(404).body(Map.of(
                "code", "USER_NOT_FOUND",
                "message", "Call POST /api/v1/auth/firebase first to create your profile."
        ));
    }
}
