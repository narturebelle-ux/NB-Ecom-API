package com.narturebelle.dto;

import jakarta.validation.constraints.NotBlank;

public class CancelBookingRequest {

    @NotBlank
    private String reason;

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
