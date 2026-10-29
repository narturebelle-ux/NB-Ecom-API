package com.narturebelle.dto;

import com.narturebelle.model.OnboardingCallbackRequest;

import java.time.LocalDateTime;

public class CallbackRequestResponse {

    private Long id;
    private Long userId;
    private String note;
    private LocalDateTime createdAt;

    public static CallbackRequestResponse from(OnboardingCallbackRequest request) {
        CallbackRequestResponse dto = new CallbackRequestResponse();
        dto.id = request.getId();
        dto.userId = request.getUserId();
        dto.note = request.getNote();
        dto.createdAt = request.getCreatedAt();
        return dto;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getNote() { return note; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
