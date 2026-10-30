package com.narturebelle.controller;

import com.narturebelle.dto.HealthProfileRequest;
import com.narturebelle.dto.HealthProfileResponse;
import com.narturebelle.exception.UserProfileNotFoundException;
import com.narturebelle.security.AuthorizationService;
import com.narturebelle.security.FirebaseUserPrincipal;
import com.narturebelle.security.RequireConsent;
import com.narturebelle.service.HealthProfileService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** The separate health section. PUT also needs the HEALTH_DATA consent (checked in the service). */
@RestController
@RequestMapping("/api/v1/me/health-profile")
public class HealthProfileController {

    private final HealthProfileService healthProfileService;
    private final AuthorizationService authorizationService;

    public HealthProfileController(HealthProfileService healthProfileService, AuthorizationService authorizationService) {
        this.healthProfileService = healthProfileService;
        this.authorizationService = authorizationService;
    }

    @RequireConsent
    @GetMapping
    public ResponseEntity<HealthProfileResponse> get() {
        Long userId = requireUserId(authorizationService.currentPrincipal());
        return ResponseEntity.ok(HealthProfileResponse.from(healthProfileService.get(userId)));
    }

    @RequireConsent
    @PutMapping
    public ResponseEntity<HealthProfileResponse> put(@RequestBody HealthProfileRequest req) {
        Long userId = requireUserId(authorizationService.currentPrincipal());
        return ResponseEntity.ok(HealthProfileResponse.from(
                healthProfileService.save(userId, req.getFirstPregnancy(), req.getMultiples(), req.getRiskFactors())));
    }

    private Long requireUserId(FirebaseUserPrincipal principal) {
        if (principal.userId() == null) {
            throw new UserProfileNotFoundException("Call POST /api/v1/auth/firebase first to create your profile.");
        }
        return principal.userId();
    }
}
