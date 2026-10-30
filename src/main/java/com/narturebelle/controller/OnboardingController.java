package com.narturebelle.controller;

import com.narturebelle.dto.BookOnboardingCallRequest;
import com.narturebelle.dto.CallbackRequestResponse;
import com.narturebelle.dto.CreateCallbackRequestRequest;
import com.narturebelle.dto.OnboardingCallResponse;
import com.narturebelle.dto.OnboardingCallSlotResponse;
import com.narturebelle.dto.OnboardingCompletionResponse;
import com.narturebelle.dto.OnboardingDocumentResponse;
import com.narturebelle.dto.OnboardingDocumentSignatureResponse;
import com.narturebelle.dto.OnboardingIntakeResponse;
import com.narturebelle.dto.OnboardingStatusResponse;
import com.narturebelle.dto.SignOnboardingDocumentRequest;
import com.narturebelle.dto.UpsertOnboardingIntakeRequest;
import com.narturebelle.exception.UserProfileNotFoundException;
import com.narturebelle.model.OnboardingCall;
import com.narturebelle.model.OnboardingCallbackRequest;
import com.narturebelle.model.OnboardingCompletion;
import com.narturebelle.model.OnboardingDocument;
import com.narturebelle.model.OnboardingDocumentSignature;
import com.narturebelle.model.OnboardingIntake;
import com.narturebelle.security.AuthorizationService;
import com.narturebelle.security.FirebaseUserPrincipal;
import com.narturebelle.security.RequireConsent;
import com.narturebelle.security.RequireVerified;
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
        OnboardingCallResponse call = onboardingService.findCall(userId)
                .map(c -> OnboardingCallResponse.from(c, onboardingService.coordinatorForCall(c).orElse(null)))
                .orElse(null);
        java.time.LocalDateTime callbackRequestedAt = onboardingService.findLatestCallbackRequest(userId)
                .map(OnboardingCallbackRequest::getCreatedAt).orElse(null);
        boolean documentsSigned = onboardingService.allActiveDocumentsSigned(userId);
        java.time.LocalDateTime completedAt = onboardingService.findCompletion(userId)
                .map(OnboardingCompletion::getCompletedAt).orElse(null);
        return ResponseEntity.ok(OnboardingStatusResponse.of(intake, call, callbackRequestedAt, documentsSigned, completedAt));
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
        Long userId = requireUserId(authorizationService.currentPrincipal());
        List<OnboardingCallSlotResponse> slots = onboardingService.listOpenCallSlots(userId).stream()
                .map(slot -> OnboardingCallSlotResponse.from(slot, onboardingService.coordinatorFor(slot).orElse(null)))
                .toList();
        return ResponseEntity.ok(slots);
    }

    @RequireConsent
    @PostMapping("/api/v1/me/onboarding/call")
    public ResponseEntity<OnboardingCallResponse> bookCall(@Valid @RequestBody BookOnboardingCallRequest req) {
        Long userId = requireUserId(authorizationService.currentPrincipal());
        OnboardingCall call = onboardingService.bookCall(userId, req);
        return ResponseEntity.ok(OnboardingCallResponse.from(call, onboardingService.coordinatorForCall(call).orElse(null)));
    }

    @RequireConsent
    @PostMapping("/api/v1/me/onboarding/callback-request")
    public ResponseEntity<CallbackRequestResponse> requestCallback(@Valid @RequestBody(required = false) CreateCallbackRequestRequest req) {
        Long userId = requireUserId(authorizationService.currentPrincipal());
        OnboardingCallbackRequest request = onboardingService.requestCallback(userId,
                req != null ? req : new CreateCallbackRequestRequest());
        return ResponseEntity.ok(CallbackRequestResponse.from(request));
    }

    @RequireConsent
    @GetMapping("/api/v1/me/onboarding/documents")
    public ResponseEntity<List<OnboardingDocumentResponse>> documents() {
        Long userId = requireUserId(authorizationService.currentPrincipal());
        List<OnboardingDocumentResponse> documents = onboardingService.listActiveDocuments().stream()
                .map(doc -> OnboardingDocumentResponse.from(doc, onboardingService.findSignature(userId, doc.getId()).isPresent()))
                .toList();
        return ResponseEntity.ok(documents);
    }

    @RequireConsent
    @RequireVerified
    @PostMapping("/api/v1/me/onboarding/documents/sign")
    public ResponseEntity<OnboardingDocumentSignatureResponse> signDocument(@Valid @RequestBody SignOnboardingDocumentRequest req) {
        Long userId = requireUserId(authorizationService.currentPrincipal());
        OnboardingDocumentSignature signature = onboardingService.signDocument(userId, req);
        return ResponseEntity.ok(OnboardingDocumentSignatureResponse.from(signature));
    }

    @RequireConsent
    @RequireVerified
    @PostMapping("/api/v1/me/onboarding/complete")
    public ResponseEntity<OnboardingCompletionResponse> complete() {
        Long userId = requireUserId(authorizationService.currentPrincipal());
        OnboardingCompletion completion = onboardingService.complete(userId);
        return ResponseEntity.ok(OnboardingCompletionResponse.from(completion));
    }

    private Long requireUserId(FirebaseUserPrincipal principal) {
        if (principal.userId() == null) {
            throw new UserProfileNotFoundException("Call POST /api/v1/auth/firebase first to create your profile.");
        }
        return principal.userId();
    }
}
