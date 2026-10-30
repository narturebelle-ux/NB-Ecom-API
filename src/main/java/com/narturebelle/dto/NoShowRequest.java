package com.narturebelle.dto;

import com.narturebelle.model.NoShowParty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class NoShowRequest {

    @NotNull
    private NoShowParty party;

    @NotBlank
    private String reason;

    public NoShowParty getParty() { return party; }
    public void setParty(NoShowParty party) { this.party = party; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
