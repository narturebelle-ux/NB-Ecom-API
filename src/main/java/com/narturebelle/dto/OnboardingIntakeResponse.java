package com.narturebelle.dto;

import com.narturebelle.model.Modality;
import com.narturebelle.model.OnboardingIntake;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

public class OnboardingIntakeResponse {

    // No health details here: they're in GET /me/health-profile.
    private Long id;
    private Set<String> supportWanted;
    private String city;
    private String language;
    private List<String> languages;
    private Modality preferredModality;
    private String phone;
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static OnboardingIntakeResponse from(OnboardingIntake intake) {
        OnboardingIntakeResponse dto = new OnboardingIntakeResponse();
        dto.id = intake.getId();
        dto.supportWanted = intake.getSupportWanted();
        dto.city = intake.getCity();
        dto.language = intake.getLanguage();
        dto.languages = List.copyOf(intake.getLanguages());
        dto.preferredModality = intake.getPreferredModality();
        dto.phone = intake.getPhone();
        dto.notes = intake.getNotes();
        dto.createdAt = intake.getCreatedAt();
        dto.updatedAt = intake.getUpdatedAt();
        return dto;
    }

    public Long getId() { return id; }
    public Set<String> getSupportWanted() { return supportWanted; }
    public String getCity() { return city; }
    public String getLanguage() { return language; }
    public List<String> getLanguages() { return languages; }
    public Modality getPreferredModality() { return preferredModality; }
    public String getPhone() { return phone; }
    public String getNotes() { return notes; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
