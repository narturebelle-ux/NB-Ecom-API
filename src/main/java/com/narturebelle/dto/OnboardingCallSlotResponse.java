package com.narturebelle.dto;

import com.narturebelle.model.Coordinator;
import com.narturebelle.model.OnboardingCallSlot;
import com.narturebelle.model.SlotStatus;

import java.time.LocalDateTime;

public class OnboardingCallSlotResponse {

    private Long id;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private SlotStatus status;
    private CoordinatorResponse coordinator;

    /** coordinator may be null (slots created before coordinators existed). */
    public static OnboardingCallSlotResponse from(OnboardingCallSlot slot, Coordinator coordinator) {
        OnboardingCallSlotResponse dto = new OnboardingCallSlotResponse();
        dto.id = slot.getId();
        dto.startAt = slot.getStartAt();
        dto.endAt = slot.getEndAt();
        dto.status = slot.getStatus();
        dto.coordinator = coordinator != null ? CoordinatorResponse.summary(coordinator) : null;
        return dto;
    }

    public Long getId() { return id; }
    public LocalDateTime getStartAt() { return startAt; }
    public LocalDateTime getEndAt() { return endAt; }
    public SlotStatus getStatus() { return status; }
    public CoordinatorResponse getCoordinator() { return coordinator; }
}
