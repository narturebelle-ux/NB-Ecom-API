package com.narturebelle.dto;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class UpdateFeatureFlagRequest {

    @NotNull
    private Boolean enabled;

    @NotBlank
    @Size(max = 255) private String reason;

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
