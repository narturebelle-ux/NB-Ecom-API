package com.narturebelle.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public class RecordVaccinationRequest {

    @NotNull
    @PastOrPresent
    private LocalDate givenAt;

    @Size(max = 255)
    private String givenWhere;

    public LocalDate getGivenAt() { return givenAt; }
    public void setGivenAt(LocalDate givenAt) { this.givenAt = givenAt; }

    public String getGivenWhere() { return givenWhere; }
    public void setGivenWhere(String givenWhere) { this.givenWhere = givenWhere; }
}
