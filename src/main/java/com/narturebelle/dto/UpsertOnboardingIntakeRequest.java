package com.narturebelle.dto;

import jakarta.validation.constraints.Pattern;
import com.narturebelle.model.Modality;
import com.narturebelle.model.RiskFactor;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Set;

public class UpsertOnboardingIntakeRequest {

    /** Transitional: health details belong in PUT /me/health-profile. If an older client still sends any of
     * these three, they're written to the health profile -- which needs the HEALTH_DATA consent. */
    private Boolean firstPregnancy;
    private Boolean multiples;
    private Set<RiskFactor> riskFactors;
    private Set<@Size(max = 100) String> supportWanted;
    @Size(max = 255) private String city;
    /** Older clients: a single language. Ignored when {@link #languages} is sent. */
    @Size(max = 50) private String language;

    /** Preferred first; 1-10 entries. When omitted, [language] is used. */
    @Size(max = 10)
    private List<@NotBlank @Size(max = 50) String> languages;

    private Modality preferredModality;
    @Pattern(regexp = InputRules.PHONE, message = InputRules.PHONE_MESSAGE) private String phone;

    @Size(max = 2000)
    private String notes;

    public Boolean getFirstPregnancy() { return firstPregnancy; }
    public void setFirstPregnancy(Boolean firstPregnancy) { this.firstPregnancy = firstPregnancy; }

    public Boolean getMultiples() { return multiples; }
    public void setMultiples(Boolean multiples) { this.multiples = multiples; }

    public Set<RiskFactor> getRiskFactors() { return riskFactors; }
    public void setRiskFactors(Set<RiskFactor> riskFactors) { this.riskFactors = riskFactors; }

    public Set<String> getSupportWanted() { return supportWanted; }
    public void setSupportWanted(Set<String> supportWanted) { this.supportWanted = supportWanted; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }

    public List<String> getLanguages() { return languages; }
    public void setLanguages(List<String> languages) { this.languages = languages; }

    public Modality getPreferredModality() { return preferredModality; }
    public void setPreferredModality(Modality preferredModality) { this.preferredModality = preferredModality; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
