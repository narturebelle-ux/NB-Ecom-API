package com.narturebelle.dto;

import com.narturebelle.model.ProviderSlot;
import com.narturebelle.model.SlotStatus;

import java.time.LocalDateTime;

public class SlotResponse {

    private Long id;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private SlotStatus status;

    public static SlotResponse from(ProviderSlot slot) {
        SlotResponse dto = new SlotResponse();
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
