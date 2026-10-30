package com.narturebelle.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * The user's health details, kept apart from the onboarding intake (product-owner decision, 2026-10-30):
 * one row per user, written only with the HEALTH_DATA consent, every read and write audited, and not
 * returned by any endpoint except /me/health-profile and the user's own data export. Product
 * recommendations may use it, but only through clinically approved rules.
 */
@Entity
@Table(name = "health_profiles")
public class HealthProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long userId;

    private Boolean firstPregnancy;

    private Boolean multiples;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "health_profile_risk_factors", joinColumns = @JoinColumn(name = "health_profile_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "risk_factor")
    private Set<RiskFactor> riskFactors = new HashSet<>();

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

    public HealthProfile() {}

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

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
