package com.narturebelle.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** A member's idea in the idea pool. Paid members' votes count toward the implementation goal. */
@Entity
@Table(name = "ideas")
public class Idea {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IdeaCategory category;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, length = 1500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IdeaStatus status;

    /** She agreed to be named on the public Idea Impact section if the idea is implemented. */
    private boolean featureConsent;

    @Column(length = 1000)
    private String impactNote;

    /** First time paid votes reached the goal (the admin inbox is emailed then). */
    private LocalDateTime goalReachedAt;

    private LocalDateTime implementedAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }

    public Idea() {}

    /** Display number, e.g. IDEA-0042. */
    public String number() {
        return String.format("IDEA-%04d", id);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public IdeaCategory getCategory() { return category; }
    public void setCategory(IdeaCategory category) { this.category = category; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public IdeaStatus getStatus() { return status; }
    public void setStatus(IdeaStatus status) { this.status = status; }

    public boolean isFeatureConsent() { return featureConsent; }
    public void setFeatureConsent(boolean featureConsent) { this.featureConsent = featureConsent; }

    public String getImpactNote() { return impactNote; }
    public void setImpactNote(String impactNote) { this.impactNote = impactNote; }

    public LocalDateTime getGoalReachedAt() { return goalReachedAt; }
    public void setGoalReachedAt(LocalDateTime goalReachedAt) { this.goalReachedAt = goalReachedAt; }

    public LocalDateTime getImplementedAt() { return implementedAt; }
    public void setImplementedAt(LocalDateTime implementedAt) { this.implementedAt = implementedAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
