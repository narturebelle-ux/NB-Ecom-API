package com.narturebelle.controller;

import com.narturebelle.dto.HospitalReferralResponse;
import com.narturebelle.service.HospitalReferralService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** /api/v1/admin/** is already role-gated by SecurityConfig (any *_ADMIN role) -- no extra check needed here. */
@RestController
@RequestMapping("/api/v1/admin/hospital-referrals")
public class AdminHospitalReferralController {

    private final HospitalReferralService hospitalReferralService;

    public AdminHospitalReferralController(HospitalReferralService hospitalReferralService) {
        this.hospitalReferralService = hospitalReferralService;
    }

    @GetMapping
    public ResponseEntity<List<HospitalReferralResponse>> list() {
        List<HospitalReferralResponse> referrals = hospitalReferralService.listAll().stream()
                .map(r -> HospitalReferralResponse.forAdmin(r, hospitalReferralService.hospitalName(r.getHospitalId())))
                .toList();
        return ResponseEntity.ok(referrals);
    }

    @PostMapping("/{id}/confirm")
    public ResponseEntity<HospitalReferralResponse> confirm(@PathVariable Long id) {
        var referral = hospitalReferralService.confirm(id);
        return ResponseEntity.ok(HospitalReferralResponse.forAdmin(referral, hospitalReferralService.hospitalName(referral.getHospitalId())));
    }
}
