package com.narturebelle.controller;

import com.narturebelle.exception.UserProfileNotFoundException;
import com.narturebelle.security.AuthorizationService;
import com.narturebelle.security.RequireConsent;
import com.narturebelle.service.ReferralService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Referral links: a member's code and progress, claiming a code after sign-up, and the public invite banner. */
@RestController
@RequestMapping("/api/v1")
public class ReferralController {

    private final ReferralService referralService;
    private final AuthorizationService authorizationService;

    public ReferralController(ReferralService referralService, AuthorizationService authorizationService) {
        this.referralService = referralService;
        this.authorizationService = authorizationService;
    }

    public record ClaimRequest(@NotBlank String code) {}

    @RequireConsent
    @GetMapping("/me/referral")
    public ResponseEntity<ReferralService.Summary> summary() {
        return ResponseEntity.ok(referralService.summary(requireUserId()));
    }

    @RequireConsent
    @PostMapping("/me/referral/claim")
    public ResponseEntity<Map<String, String>> claim(@RequestBody @jakarta.validation.Valid ClaimRequest req) {
        return ResponseEntity.ok(Map.of("referrerName", referralService.claim(requireUserId(), req.code())));
    }

    /** Public (no token): who invited you, for the landing banner. 404 REFERRAL_CODE_NOT_FOUND for a bad link. */
    @GetMapping("/referrals/{code}")
    public ResponseEntity<Map<String, String>> invite(@PathVariable String code) {
        return ResponseEntity.ok(Map.of("referrerName", referralService.referrerName(code)));
    }

    private Long requireUserId() {
        Long userId = authorizationService.currentPrincipal().userId();
        if (userId == null) {
            throw new UserProfileNotFoundException("Call POST /api/v1/auth/firebase first to create your profile.");
        }
        return userId;
    }
}
