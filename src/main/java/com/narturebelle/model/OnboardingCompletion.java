package com.narturebelle.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** One row per user, created the moment POST /me/onboarding/complete's checks all pass. Its mere
 * existence is "onboarded" (decision log #12: the user, by signing, not a coordinator). */
@Entity
@Table(name = "onboarding_completions")
public class OnboardingCompletion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long userId;

    @Column(nullable = false)
    private LocalDateTime completedAt;

    @PrePersist
    protected void onCreate() {
        if (this.completedAt == null) {
            this.completedAt = LocalDateTime.now();
        }
    }

    public OnboardingCompletion() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
}
