package com.narturebelle.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** A member's personal referral code, shared as a link (e.g. narturebelle.com/?ref=AB3K7Q9M). */
@Entity
@Table(name = "referral_codes")
public class ReferralCode {

    @Id
    private Long userId;

    @Column(nullable = false, unique = true, length = 12)
    private String code;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }

    public ReferralCode() {}

    public ReferralCode(Long userId, String code) {
        this.userId = userId;
        this.code = code;
    }

    public Long getUserId() { return userId; }
    public String getCode() { return code; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
