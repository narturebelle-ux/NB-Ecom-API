package com.narturebelle.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** Fallback for when no OnboardingCallSlot is available -- the user asks to be called back instead
 * of booking a specific slot. There's no coordinator portal, so this is surfaced to admins only
 * via GET /admin/onboarding/callback-requests, same reasoning as every other "no portal yet" gap. */
@Entity
@Table(name = "onboarding_callback_requests")
public class OnboardingCallbackRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(length = 500)
    private String note;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }

    public OnboardingCallbackRequest() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
