package com.narturebelle.dto;

import com.narturebelle.model.JourneyStatus;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public class CreateJourneyRequest {

    @NotNull
    private JourneyStatus status;

    private LocalDate expectedDeliveryDate;

    public JourneyStatus getStatus() { return status; }
    public void setStatus(JourneyStatus status) { this.status = status; }

    public LocalDate getExpectedDeliveryDate() { return expectedDeliveryDate; }
    public void setExpectedDeliveryDate(LocalDate expectedDeliveryDate) { this.expectedDeliveryDate = expectedDeliveryDate; }
}
