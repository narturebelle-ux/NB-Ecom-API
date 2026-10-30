package com.narturebelle.dto;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;

public class CancelBookingRequest {

    @NotBlank
    @Size(max = 255) private String reason;

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
