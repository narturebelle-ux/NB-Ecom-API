package com.narturebelle.dto;

import com.narturebelle.model.ChildSex;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** Every field optional -- only the ones present are changed. */
public class PatchChildRequest {

    @Size(max = 255)
    @Pattern(regexp = ".*\\S.*", message = "must not be blank")
    private String name;

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
