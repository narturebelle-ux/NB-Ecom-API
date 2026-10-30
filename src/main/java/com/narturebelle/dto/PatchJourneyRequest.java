package com.narturebelle.dto;

import com.narturebelle.model.JourneyStatus;

import java.time.LocalDate;

/** Both fields optional — only non-null values are applied, same convention as UpdateProfileRequest. */
public class PatchJourneyRequest {

    private JourneyStatus status;
    private LocalDate expectedDeliveryDate;

    public JourneyStatus getStatus() { return status; }
    public void setStatus(JourneyStatus status) { this.status = status; }

    public LocalDate getExpectedDeliveryDate() { return expectedDeliveryDate; }
    public void setExpectedDeliveryDate(LocalDate expectedDeliveryDate) { this.expectedDeliveryDate = expectedDeliveryDate; }
}
