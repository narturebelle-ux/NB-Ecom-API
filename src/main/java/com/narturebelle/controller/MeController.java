package com.narturebelle.controller;

import com.narturebelle.dto.MeProfileResponse;
import com.narturebelle.dto.UpdateProfileRequest;
import com.narturebelle.model.User;
import com.narturebelle.security.RequireVerified;
import com.narturebelle.service.ConsentService;
import com.narturebelle.service.UserProfileService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * The real, current self-service endpoints — /api/v1/users/me (GET/PUT) still exists as a
 * deprecated alias delegating to the same UserProfileService, for one release.
 */
@RestController
@RequestMapping("/api/v1/me")
public class MeController {

    private final UserProfileService userProfileService;
    private final ConsentService consentService;

    public MeController(UserProfileService userProfileService, ConsentService consentService) {
        this.userProfileService = userProfileService;
        this.consentService = consentService;
    }

    /** hasActiveJourney is a placeholder until Journey (1.2) ships. */
    @GetMapping
    public ResponseEntity<MeProfileResponse> getMe() {
        User user = userProfileService.loadSelfOrThrow();
        return ResponseEntity.ok(MeProfileResponse.from(user, consentService.pendingConsentTypes(user.getId()), false));
    }

    @RequireVerified
    @PatchMapping
    public ResponseEntity<MeProfileResponse> updateMe(@Valid @RequestBody UpdateProfileRequest req) {
        User user = userProfileService.loadSelfOrThrow();
        User saved = userProfileService.applyProfileUpdate(user, req);
        return ResponseEntity.ok(MeProfileResponse.from(saved, consentService.pendingConsentTypes(saved.getId()), false));
    }
}
