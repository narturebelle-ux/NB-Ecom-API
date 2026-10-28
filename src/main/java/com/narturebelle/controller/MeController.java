package com.narturebelle.controller;

import com.narturebelle.dto.MeProfileResponse;
import com.narturebelle.dto.UpdateProfileRequest;
import com.narturebelle.model.User;
import com.narturebelle.security.RequireVerified;
import com.narturebelle.service.UserProfileService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * The real, current self-service endpoints — /api/v1/users/me (GET/PUT) still exists as a
 * deprecated alias delegating to the same UserProfileService, for one release.
 */
@RestController
@RequestMapping("/api/v1/me")
public class MeController {

    private final UserProfileService userProfileService;

    public MeController(UserProfileService userProfileService) {
        this.userProfileService = userProfileService;
    }

    /** pendingConsents/hasActiveJourney are placeholders until Consent (1.1) and Journey (1.2) ship. */
    @GetMapping
    public ResponseEntity<MeProfileResponse> getMe() {
        User user = userProfileService.loadSelfOrThrow();
        return ResponseEntity.ok(MeProfileResponse.from(user, List.of(), false));
    }

    @RequireVerified
    @PatchMapping
    public ResponseEntity<MeProfileResponse> updateMe(@Valid @RequestBody UpdateProfileRequest req) {
        User user = userProfileService.loadSelfOrThrow();
        User saved = userProfileService.applyProfileUpdate(user, req);
        return ResponseEntity.ok(MeProfileResponse.from(saved, List.of(), false));
    }
}
