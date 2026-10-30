package com.narturebelle.dto;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;

public class CompleteSessionRequest {

    @NotBlank
    @Size(max = 5000) private String note;

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
