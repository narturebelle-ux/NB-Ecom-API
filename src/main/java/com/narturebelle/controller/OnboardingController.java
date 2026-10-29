package com.narturebelle.controller;

import com.narturebelle.dto.BookOnboardingCallRequest;
import com.narturebelle.dto.CallbackRequestResponse;
import com.narturebelle.dto.CreateCallbackRequestRequest;
import com.narturebelle.dto.OnboardingCallResponse;
import com.narturebelle.dto.OnboardingCallSlotResponse;
import com.narturebelle.dto.OnboardingIntakeResponse;
import com.narturebelle.dto.OnboardingStatusResponse;
import com.narturebelle.dto.UpsertOnboardingIntakeRequest;
import com.narturebelle.exception.UserProfileNotFoundException;
import com.narturebelle.model.OnboardingCall;
import com.narturebelle.model.OnboardingCallbackRequest;
import com.narturebelle.model.OnboardingIntake;
import com.narturebelle.security.AuthorizationService;
import com.narturebelle.security.FirebaseUserPrincipal;
import com.narturebelle.security.RequireConsent;
import com.narturebelle.service.OnboardingService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class OnboardingController {

    private final OnboardingService onboardingService;
    private final AuthorizationService authorizationService;

    public OnboardingController(OnboardingService onboardingService, AuthorizationService authorizationService) {
        this.onboardingService = onboardingService;
        this.authorizationService = authorizationService;
    }

    @RequireConsent
    @GetMapping("/api/v1/me/onboarding")
    public ResponseEntity<OnboardingStatusResponse> status() {
        Long userId = requireUserId(authorizationService.currentPrincipal());
        OnboardingIntakeResponse intake = onboardingService.findIntake(userId).map(OnboardingIntakeResponse::from).orElse(null);
        OnboardingCallResponse call = onboardingService.findCall(userId).map(OnboardingCallResponse::from).orElse(null);
        java.time.LocalDateTime callbackRequestedAt = onboardingService.findLatestCallbackRequest(userId)
                .map(com.narturebelle.model.OnboardingCallbackRequest::getCreatedAt).orElse(null);
        return ResponseEntity.ok(OnboardingStatusResponse.of(intake, call, callbackRequestedAt, false, null));
    }

    @RequireConsent
    @PutMapping("/api/v1/me/onboarding/intake")
    public ResponseEntity<OnboardingIntakeResponse> upsertIntake(@Valid @RequestBody UpsertOnboardingIntakeRequest req) {
        Long userId = requireUserId(authorizationService.currentPrincipal());
        OnboardingIntake intake = onboardingService.upsertIntake(userId, req);
        return ResponseEntity.ok(OnboardingIntakeResponse.from(intake));
    }

    @RequireConsent
    @GetMapping("/api/v1/onboarding/call-slots")
    public ResponseEntity<List<OnboardingCallSlotResponse>> callSlots() {
        List<OnboardingCallSlotResponse> slots = onboardingService.listOpenCallSlots().stream()
                .map(OnboardingCallSlotResponse::from).toList();
        return ResponseEntity.ok(slots);
    }

    @RequireConsent
    @PostMapping("/api/v1/me/onboarding/call")
    public ResponseEntity<OnboardingCallResponse> bookCall(@Valid @RequestBody BookOnboardingCallRequest req) {
        Long userId = requireUserId(authorizationService.currentPrincipal());
        OnboardingCall call = onboardingService.bookCall(userId, req);
        return ResponseEntity.ok(OnboardingCallResponse.from(call));
    }

    @RequireConsent
    @PostMapping("/api/v1/me/onboarding/callback-request")
    public ResponseEntity<CallbackRequestResponse> requestCallback(@Valid @RequestBody(required = false) CreateCallbackRequestRequest req) {
        Long userId = requireUserId(authorizationService.currentPrincipal());
        OnboardingCallbackRequest request = onboardingService.requestCallback(userId,
                req != null ? req : new CreateCallbackRequestRequest());
        return ResponseEntity.ok(CallbackRequestResponse.from(request));
    }

    private Long requireUserId(FirebaseUserPrincipal principal) {
        if (principal.userId() == null) {
            throw new UserProfileNotFoundException("Call POST /api/v1/auth/firebase first to create your profile.");
        }
        return principal.userId();
    }
}
