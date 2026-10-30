package com.narturebelle.controller;

import com.narturebelle.dto.UpdateProfileRequest;
import com.narturebelle.dto.UserProfileResponse;
import com.narturebelle.model.User;
import com.narturebelle.repository.UserRepository;
import com.narturebelle.security.AuthorizationService;
import com.narturebelle.security.RequireVerified;
import com.narturebelle.service.UserProfileService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserRepository userRepository;
    private final AuthorizationService authorizationService;
    private final UserProfileService userProfileService;

    public UserController(UserRepository userRepository, AuthorizationService authorizationService,
                           UserProfileService userProfileService) {
        this.userRepository = userRepository;
        this.authorizationService = authorizationService;
        this.userProfileService = userProfileService;
    }

    /** @deprecated use GET /api/v1/me — kept as an alias for one release. */
    @Deprecated
    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getMyProfile() {
        User user = userProfileService.loadSelfOrThrow();
        return ResponseEntity.ok(UserProfileResponse.from(user));
    }

    /** @deprecated use PATCH /api/v1/me — kept as an alias for one release. */
    @Deprecated
    @RequireVerified
    @PutMapping("/me")
    public ResponseEntity<UserProfileResponse> updateMyProfile(@Valid @RequestBody UpdateProfileRequest req) {
        User user = userProfileService.loadSelfOrThrow();
        User saved = userProfileService.applyProfileUpdate(user, req);
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
}
