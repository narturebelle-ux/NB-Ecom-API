package com.narturebelle.dto;

import java.time.LocalDateTime;

/** documentsSigned/completedAt are always false/null for now -- document e-signing and the completion
 * endpoint land once Phase 5's notification service exists (signing emails the signed copy). */
public class OnboardingStatusResponse {

    private OnboardingIntakeResponse intake;
    private OnboardingCallResponse call;
    private LocalDateTime callbackRequestedAt;
    private boolean documentsSigned;
    private LocalDateTime completedAt;

    public static OnboardingStatusResponse of(OnboardingIntakeResponse intake, OnboardingCallResponse call,
                                               LocalDateTime callbackRequestedAt, boolean documentsSigned,
                                               LocalDateTime completedAt) {
        OnboardingStatusResponse dto = new OnboardingStatusResponse();
        dto.intake = intake;
        dto.call = call;
        dto.callbackRequestedAt = callbackRequestedAt;
        dto.documentsSigned = documentsSigned;
        dto.completedAt = completedAt;
        return dto;
    }

    public OnboardingIntakeResponse getIntake() { return intake; }
    public OnboardingCallResponse getCall() { return call; }
    public LocalDateTime getCallbackRequestedAt() { return callbackRequestedAt; }
    public boolean isDocumentsSigned() { return documentsSigned; }
    public LocalDateTime getCompletedAt() { return completedAt; }
}
