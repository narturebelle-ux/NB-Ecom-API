package com.narturebelle.dto;

import jakarta.validation.constraints.Size;
import com.narturebelle.model.ProviderStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class UpdateProviderStatusRequest {

    @NotNull
    private ProviderStatus status;

    @NotBlank
    @Size(max = 255) private String reason;

    public ProviderStatus getStatus() { return status; }
    public void setStatus(ProviderStatus status) { this.status = status; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
