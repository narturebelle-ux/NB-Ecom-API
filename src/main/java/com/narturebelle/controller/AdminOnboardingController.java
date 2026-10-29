package com.narturebelle.controller;

import com.narturebelle.dto.CreateOnboardingCallSlotsRequest;
import com.narturebelle.dto.OnboardingCallSlotResponse;
import com.narturebelle.service.OnboardingService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** /api/v1/admin/** is already role-gated by SecurityConfig (any *_ADMIN role) -- no extra check needed here. */
@RestController
@RequestMapping("/api/v1/admin/onboarding")
public class AdminOnboardingController {

    private final OnboardingService onboardingService;

    public AdminOnboardingController(OnboardingService onboardingService) {
        this.onboardingService = onboardingService;
    }

    @PostMapping("/call-slots")
    public ResponseEntity<List<OnboardingCallSlotResponse>> createCallSlots(@Valid @RequestBody CreateOnboardingCallSlotsRequest req) {
        List<OnboardingCallSlotResponse> slots = onboardingService.createCallSlots(req).stream()
                .map(OnboardingCallSlotResponse::from).toList();
        return ResponseEntity.ok(slots);
    }
}
