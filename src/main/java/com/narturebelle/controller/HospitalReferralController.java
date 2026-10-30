package com.narturebelle.controller;

import com.narturebelle.dto.CreateHospitalReferralRequest;
import com.narturebelle.dto.HospitalReferralResponse;
import com.narturebelle.exception.UserProfileNotFoundException;
import com.narturebelle.model.HospitalReferral;
import com.narturebelle.security.AuthorizationService;
import com.narturebelle.security.FirebaseUserPrincipal;
import com.narturebelle.security.RequireConsent;
import com.narturebelle.security.RequireVerified;
import com.narturebelle.service.HospitalReferralService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Hospital referrals with attribution codes (Phase 6). The hospital directory itself stays on /api/v1/hospitals. */
@RestController
@RequestMapping("/api/v1/me/hospital-referrals")
public class HospitalReferralController {

    private final HospitalReferralService hospitalReferralService;
    private final AuthorizationService authorizationService;

    public HospitalReferralController(HospitalReferralService hospitalReferralService, AuthorizationService authorizationService) {
        this.hospitalReferralService = hospitalReferralService;
        this.authorizationService = authorizationService;
    }

    @RequireConsent
    @RequireVerified
    @PostMapping
    public ResponseEntity<HospitalReferralResponse> create(@Valid @RequestBody CreateHospitalReferralRequest req) {
        Long userId = requireUserId(authorizationService.currentPrincipal());
        HospitalReferral referral = hospitalReferralService.createReferral(userId, req);
        return ResponseEntity.ok(HospitalReferralResponse.from(referral, hospitalReferralService.hospitalName(referral.getHospitalId())));
    }

    @RequireConsent
    @GetMapping
    public ResponseEntity<List<HospitalReferralResponse>> list() {
        Long userId = requireUserId(authorizationService.currentPrincipal());
        List<HospitalReferralResponse> referrals = hospitalReferralService.listForUser(userId).stream()
                .map(r -> HospitalReferralResponse.from(r, hospitalReferralService.hospitalName(r.getHospitalId())))
                .toList();
        return ResponseEntity.ok(referrals);
    }

    private Long requireUserId(FirebaseUserPrincipal principal) {
        if (principal.userId() == null) {
            throw new UserProfileNotFoundException("Call POST /api/v1/auth/firebase first to create your profile.");
        }
        return principal.userId();
    }
}
