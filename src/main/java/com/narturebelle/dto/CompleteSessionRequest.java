package com.narturebelle.dto;

import jakarta.validation.constraints.NotBlank;

public class CompleteSessionRequest {

    @NotBlank
    private String note;

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
