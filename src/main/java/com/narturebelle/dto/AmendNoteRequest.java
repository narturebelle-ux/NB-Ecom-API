package com.narturebelle.dto;

import jakarta.validation.constraints.NotBlank;

public class AmendNoteRequest {

    @NotBlank
    private String note;

    @NotBlank
    private String reason;

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
