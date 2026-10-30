package com.narturebelle.controller;

import com.narturebelle.dto.SessionProductRecommendationResponse;
import com.narturebelle.exception.UserProfileNotFoundException;
import com.narturebelle.security.AuthorizationService;
import com.narturebelle.security.FirebaseUserPrincipal;
import com.narturebelle.security.RequireConsent;
import com.narturebelle.service.ProviderPortalService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Products her professionals picked for her, from completed sessions. ?bookingId= narrows to one session (the post-session page). */
@RestController
@RequestMapping("/api/v1/me/care-recommendations")
public class CareRecommendationController {

    private final ProviderPortalService portalService;
    private final AuthorizationService authorizationService;

    public CareRecommendationController(ProviderPortalService portalService, AuthorizationService authorizationService) {
        this.portalService = portalService;
        this.authorizationService = authorizationService;
    }

    @RequireConsent
    @GetMapping
    public ResponseEntity<List<SessionProductRecommendationResponse>> list(@RequestParam(required = false) Long bookingId) {
        Long userId = requireUserId(authorizationService.currentPrincipal());
        return ResponseEntity.ok(portalService.forMother(userId, bookingId));
    }

    private Long requireUserId(FirebaseUserPrincipal principal) {
        if (principal.userId() == null) {
            throw new UserProfileNotFoundException("Call POST /api/v1/auth/firebase first to create your profile.");
        }
        return principal.userId();
    }
}
