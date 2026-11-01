package com.narturebelle.controller;

import com.narturebelle.dto.EntitlementResponse;
import com.narturebelle.exception.UserProfileNotFoundException;
import com.narturebelle.security.AuthorizationService;
import com.narturebelle.security.FirebaseUserPrincipal;
import com.narturebelle.service.EntitlementService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Authenticated only (no @RequireVerified) — viewing the wallet is lower-stakes than paying, same as GET /me vs PATCH /me. */
@RestController
@RequestMapping("/api/v1/entitlements")
public class EntitlementController {

    private final EntitlementService entitlementService;
    private final AuthorizationService authorizationService;

    public EntitlementController(EntitlementService entitlementService, AuthorizationService authorizationService) {
        this.entitlementService = entitlementService;
        this.authorizationService = authorizationService;
    }

    @GetMapping
    public ResponseEntity<List<EntitlementResponse>> list() {
        FirebaseUserPrincipal principal = authorizationService.currentPrincipal();
        if (principal.userId() == null) {
            throw new UserProfileNotFoundException("Call POST /api/v1/auth/firebase first to create your profile.");
        }
        List<EntitlementResponse> wallet = entitlementService.walletForUser(principal.userId()).stream()
                .map(e -> EntitlementResponse.from(e, e.getPlanCode()))
                .toList();
        return ResponseEntity.ok(wallet);
    }
}
