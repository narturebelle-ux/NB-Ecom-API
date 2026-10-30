package com.narturebelle.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** One row per user (upserted via PUT), separate from Journey — stage/EDD stay on Journey, this holds the rest of the intake. */
@Entity
@Table(name = "onboarding_intakes")
public class OnboardingIntake {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long userId;

    private Boolean firstPregnancy;

    private Boolean multiples;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "onboarding_intake_risk_factors", joinColumns = @JoinColumn(name = "intake_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "risk_factor")
    private Set<RiskFactor> riskFactors = new HashSet<>();

    /** Free-form selections (e.g. "Nutrition guidance", "Mental health support") — kept as strings rather than a
     * closed enum since the UI's exact option set isn't part of this API's contract. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "onboarding_intake_support_wanted", joinColumns = @JoinColumn(name = "intake_id"))
    @Column(name = "support_wanted")
    private Set<String> supportWanted = new HashSet<>();

    private String city;

    /** Kept for older clients: always the first of {@link #languages}. */
    private String language;

    /** Every language she's comfortable in, in her order of preference -- used to match providers and coordinators. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "onboarding_intake_languages", joinColumns = @JoinColumn(name = "intake_id"))
    @OrderColumn(name = "position")
    @Column(name = "language")
    private List<String> languages = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    private Modality preferredModality;

    private String phone;

    @Column(length = 2000)
    private String notes;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public OnboardingIntake() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

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

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
