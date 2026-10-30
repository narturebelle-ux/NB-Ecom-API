package com.narturebelle.dto;

import jakarta.validation.constraints.NotNull;

public class BookOnboardingCallRequest {

    @NotNull
    private Long slotId;

    public Long getSlotId() { return slotId; }
    public void setSlotId(Long slotId) { this.slotId = slotId; }
}
