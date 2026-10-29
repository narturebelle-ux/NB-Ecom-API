package com.narturebelle.dto;

import jakarta.validation.constraints.Size;

public class CreateCallbackRequestRequest {

    @Size(max = 500)
    private String note;

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
