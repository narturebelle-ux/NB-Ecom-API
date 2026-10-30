package com.narturebelle.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class CreateHospitalReferralRequest {

    @NotNull
    private Long hospitalId;

    /** Free text the user chooses to pass to the hospital (e.g. a call-back name/number). */
    @Size(max = 1000)
    private String note;

    public Long getHospitalId() { return hospitalId; }
    public void setHospitalId(Long hospitalId) { this.hospitalId = hospitalId; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
