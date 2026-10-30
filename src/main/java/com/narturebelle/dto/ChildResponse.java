package com.narturebelle.dto;

import com.narturebelle.model.Child;
import com.narturebelle.model.ChildSex;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class ChildResponse {

    private Long id;
    private String name;
    private LocalDate dateOfBirth;
    private ChildSex sex;
    private LocalDateTime createdAt;

    public static ChildResponse from(Child child) {
        ChildResponse dto = new ChildResponse();
        dto.id = child.getId();
        dto.name = child.getName();
        dto.dateOfBirth = child.getDateOfBirth();
        dto.sex = child.getSex();
        dto.createdAt = child.getCreatedAt();
        return dto;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public ChildSex getSex() { return sex; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
