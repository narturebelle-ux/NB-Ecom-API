package com.narturebelle.dto;

import com.narturebelle.model.OnboardingCall;

import java.time.LocalDateTime;

public class OnboardingCallResponse {

    private Long id;
    private Long slotId;
    private LocalDateTime scheduledStart;
    private LocalDateTime scheduledEnd;
    private LocalDateTime createdAt;

    public static OnboardingCallResponse from(OnboardingCall call) {
        OnboardingCallResponse dto = new OnboardingCallResponse();
        dto.id = call.getId();
        dto.slotId = call.getSlotId();
        dto.scheduledStart = call.getScheduledStart();
        dto.scheduledEnd = call.getScheduledEnd();
        dto.createdAt = call.getCreatedAt();
        return dto;
    }

    public Long getId() { return id; }
    public Long getSlotId() { return slotId; }
    public LocalDateTime getScheduledStart() { return scheduledStart; }
    public LocalDateTime getScheduledEnd() { return scheduledEnd; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
