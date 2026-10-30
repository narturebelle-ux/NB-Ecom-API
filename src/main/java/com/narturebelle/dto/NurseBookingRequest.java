package com.narturebelle.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import com.narturebelle.model.PregnancyStage;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public class NurseBookingRequest {
    @NotBlank
    @Size(max = 255) private String clientName;

    @NotBlank
    @Pattern(regexp = InputRules.PHONE, message = InputRules.PHONE_MESSAGE) private String clientPhone;

    @Email
    @Size(max = 254) private String clientEmail;
    private PregnancyStage currentStage;
    @Size(max = 255) private String preferredLanguage;
    private Long packageId;
    @Size(max = 255) private String city;
    @Size(max = 1500) private String notes;

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
