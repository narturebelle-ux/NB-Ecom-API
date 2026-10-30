package com.narturebelle.dto;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;

public class AmendNoteRequest {

    @NotBlank
    @Size(max = 5000) private String note;

    @NotBlank
    @Size(max = 255) private String reason;

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
