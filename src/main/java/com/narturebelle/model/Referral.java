package com.narturebelle.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** A friend who joined through a member's link. Each member can be referred once. It counts toward the
 * referrer's reward once the friend has a paid order (qualifiedAt). */
@Entity
@Table(name = "referrals")
public class Referral {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long referrerUserId;

    @Column(nullable = false, unique = true)
    private Long referredUserId;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime qualifiedAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }

    public Referral() {}

    public Referral(Long referrerUserId, Long referredUserId) {
        this.referrerUserId = referrerUserId;
        this.referredUserId = referredUserId;
    }

    public Long getId() { return id; }
    public Long getReferrerUserId() { return referrerUserId; }
    public Long getReferredUserId() { return referredUserId; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public LocalDateTime getQualifiedAt() { return qualifiedAt; }
    public void setQualifiedAt(LocalDateTime qualifiedAt) { this.qualifiedAt = qualifiedAt; }
}
