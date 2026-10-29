package com.narturebelle.dto;

import com.narturebelle.model.Modality;
import com.narturebelle.model.OnboardingIntake;
import com.narturebelle.model.RiskFactor;

import java.time.LocalDateTime;
import java.util.Set;

public class OnboardingIntakeResponse {

    private Long id;
    private Boolean firstPregnancy;
    private Boolean multiples;
    private Set<RiskFactor> riskFactors;
    private Set<String> supportWanted;
    private String city;
    private String language;
    private Modality preferredModality;
    private String phone;
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static OnboardingIntakeResponse from(OnboardingIntake intake) {
        OnboardingIntakeResponse dto = new OnboardingIntakeResponse();
        dto.id = intake.getId();
        dto.firstPregnancy = intake.getFirstPregnancy();
        dto.multiples = intake.getMultiples();
        dto.riskFactors = intake.getRiskFactors();
        dto.supportWanted = intake.getSupportWanted();
        dto.city = intake.getCity();
        dto.language = intake.getLanguage();
        dto.preferredModality = intake.getPreferredModality();
        dto.phone = intake.getPhone();
        dto.notes = intake.getNotes();
        dto.createdAt = intake.getCreatedAt();
        dto.updatedAt = intake.getUpdatedAt();
        return dto;
    }

    public Long getId() { return id; }
    public Boolean getFirstPregnancy() { return firstPregnancy; }
    public Boolean getMultiples() { return multiples; }
    public Set<RiskFactor> getRiskFactors() { return riskFactors; }
    public Set<String> getSupportWanted() { return supportWanted; }
    public String getCity() { return city; }
    public String getLanguage() { return language; }
    public Modality getPreferredModality() { return preferredModality; }
    public String getPhone() { return phone; }
    public String getNotes() { return notes; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
