package com.narturebelle.controller;

import com.narturebelle.dto.CareMatchesResponse;
import com.narturebelle.exception.UserProfileNotFoundException;
import com.narturebelle.model.Modality;
import com.narturebelle.model.Role;
import com.narturebelle.security.AuthorizationService;
import com.narturebelle.security.FirebaseUserPrincipal;
import com.narturebelle.security.RequireConsent;
import com.narturebelle.service.CareMatchService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Providers matched to the signed-in user by language, city/modality and open slots. The public directory stays on /providers. */
@RestController
@RequestMapping("/api/v1/me/care/matches")
public class CareMatchController {

    private final CareMatchService careMatchService;
    private final AuthorizationService authorizationService;

    public CareMatchController(CareMatchService careMatchService, AuthorizationService authorizationService) {
        this.careMatchService = careMatchService;
        this.authorizationService = authorizationService;
    }

    @RequireConsent
    @GetMapping
    public ResponseEntity<CareMatchesResponse> matches(@RequestParam Role professionalType,
                                                       @RequestParam(required = false) Modality modality) {
        Long userId = requireUserId(authorizationService.currentPrincipal());
        return ResponseEntity.ok(careMatchService.match(userId, professionalType, modality));
    }

    private Long requireUserId(FirebaseUserPrincipal principal) {
        if (principal.userId() == null) {
            throw new UserProfileNotFoundException("Call POST /api/v1/auth/firebase first to create your profile.");
        }
        return principal.userId();
    }
}
