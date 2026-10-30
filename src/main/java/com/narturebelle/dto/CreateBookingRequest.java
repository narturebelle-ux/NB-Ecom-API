package com.narturebelle.dto;

import jakarta.validation.constraints.NotNull;

public class CreateBookingRequest {

    @NotNull
    private Long providerId;

    @NotNull
    private Long serviceOfferingId;

    @NotNull
    private Long slotId;

    public Long getProviderId() { return providerId; }
    public void setProviderId(Long providerId) { this.providerId = providerId; }

    public Long getServiceOfferingId() { return serviceOfferingId; }
    public void setServiceOfferingId(Long serviceOfferingId) { this.serviceOfferingId = serviceOfferingId; }

    public Long getSlotId() { return slotId; }
    public void setSlotId(Long slotId) { this.slotId = slotId; }
}
