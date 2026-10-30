package com.narturebelle.dto;

import com.narturebelle.model.ChildSex;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public class CreateChildRequest {

    @NotBlank
    @Size(max = 255)
    private String name;

    @NotNull
    @PastOrPresent
    private LocalDate dateOfBirth;

    private ChildSex sex;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }

    public ChildSex getSex() { return sex; }
    public void setSex(ChildSex sex) { this.sex = sex; }
}
