package com.narturebelle.controller;

import com.narturebelle.dto.UserProfileResponse;
import com.narturebelle.model.User;
import com.narturebelle.repository.UserRepository;
import com.narturebelle.security.AuthorizationService;
import com.narturebelle.security.FirebaseUserPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final AuthorizationService authorizationService;

    public AuthController(UserRepository userRepository, AuthorizationService authorizationService) {
        this.userRepository = userRepository;
        this.authorizationService = authorizationService;
    }

    /**
     * Called by the UI right after any sign-in. Requires Authorization: Bearer <Firebase ID token>
     * (verified by FirebaseAuthenticationFilter before this method runs). Creates the user on first
     * login, or updates email/phone/verification status from the token on subsequent logins.
     */
    @PostMapping("/firebase")
    public ResponseEntity<UserProfileResponse> syncFirebaseUser() {
        FirebaseUserPrincipal principal = authorizationService.currentPrincipal();

        User user = userRepository.findByFirebaseUid(principal.firebaseUid()).orElseGet(() -> {
            User newUser = new User();
            newUser.setFirebaseUid(principal.firebaseUid());
            newUser.setRole("USER");
            return newUser;
        });

        user.setEmail(principal.email());
        user.setEmailVerified(principal.emailVerified());
        if (principal.phone() != null) {
            user.setPhone(principal.phone());
            user.setPhoneVerified(true);
        }

        User saved = userRepository.save(user);
        return ResponseEntity.ok(UserProfileResponse.from(saved));
    }
}
