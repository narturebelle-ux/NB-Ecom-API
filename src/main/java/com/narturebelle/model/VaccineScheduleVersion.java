package com.narturebelle.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** Versioned vaccine schedule ("config not code", same convention as ConsentDocument/OnboardingDocument).
 * Only one version should be active=true at a time. Seeded PENDING_CLINICAL_REVIEW -- not an approved schedule. */
@Entity
@Table(name = "vaccine_schedule_versions")
public class VaccineScheduleVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Integer version;

    @Column(nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReviewStatus reviewStatus;

    private LocalDateTime effectiveAt;

    private boolean active;

    public VaccineScheduleVersion() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public ReviewStatus getReviewStatus() { return reviewStatus; }
    public void setReviewStatus(ReviewStatus reviewStatus) { this.reviewStatus = reviewStatus; }

    public LocalDateTime getEffectiveAt() { return effectiveAt; }
    public void setEffectiveAt(LocalDateTime effectiveAt) { this.effectiveAt = effectiveAt; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
