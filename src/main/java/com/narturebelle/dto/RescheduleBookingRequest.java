package com.narturebelle.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class RescheduleBookingRequest {

    @NotNull
    private Long newSlotId;

    @NotBlank
    private String reason;

    public Long getNewSlotId() { return newSlotId; }
    public void setNewSlotId(Long newSlotId) { this.newSlotId = newSlotId; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
