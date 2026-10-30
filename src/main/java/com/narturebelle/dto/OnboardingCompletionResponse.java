package com.narturebelle.dto;

import com.narturebelle.model.OnboardingCompletion;

import java.time.LocalDateTime;

public class OnboardingCompletionResponse {

    private LocalDateTime completedAt;

    public static OnboardingCompletionResponse from(OnboardingCompletion completion) {
        OnboardingCompletionResponse dto = new OnboardingCompletionResponse();
        dto.completedAt = completion.getCompletedAt();
        return dto;
    }

    public LocalDateTime getCompletedAt() { return completedAt; }
}
