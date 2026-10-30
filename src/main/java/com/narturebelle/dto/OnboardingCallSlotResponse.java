package com.narturebelle.dto;

import com.narturebelle.model.OnboardingCallSlot;
import com.narturebelle.model.SlotStatus;

import java.time.LocalDateTime;

public class OnboardingCallSlotResponse {

    private Long id;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private SlotStatus status;

    public static OnboardingCallSlotResponse from(OnboardingCallSlot slot) {
        OnboardingCallSlotResponse dto = new OnboardingCallSlotResponse();
        dto.id = slot.getId();
        dto.startAt = slot.getStartAt();
        dto.endAt = slot.getEndAt();
        dto.status = slot.getStatus();
        return dto;
    }

    public Long getId() { return id; }
    public LocalDateTime getStartAt() { return startAt; }
    public LocalDateTime getEndAt() { return endAt; }
    public SlotStatus getStatus() { return status; }
}
