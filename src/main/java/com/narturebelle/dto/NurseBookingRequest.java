package com.narturebelle.dto;

import com.narturebelle.model.PregnancyStage;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public class NurseBookingRequest {
    @NotBlank
    private String clientName;

    @NotBlank
    private String clientPhone;

    @Email
    private String clientEmail;
    private PregnancyStage currentStage;
    private String preferredLanguage;
    private Long packageId;
    private String city;
    private String notes;

    public NurseBookingRequest() {}

    public String getClientName() { return clientName; }
    public void setClientName(String clientName) { this.clientName = clientName; }

    public String getClientPhone() { return clientPhone; }
    public void setClientPhone(String clientPhone) { this.clientPhone = clientPhone; }

    public String getClientEmail() { return clientEmail; }
    public void setClientEmail(String clientEmail) { this.clientEmail = clientEmail; }

    public PregnancyStage getCurrentStage() { return currentStage; }
    public void setCurrentStage(PregnancyStage currentStage) { this.currentStage = currentStage; }

    public String getPreferredLanguage() { return preferredLanguage; }
    public void setPreferredLanguage(String preferredLanguage) { this.preferredLanguage = preferredLanguage; }

    public Long getPackageId() { return packageId; }
    public void setPackageId(Long packageId) { this.packageId = packageId; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
