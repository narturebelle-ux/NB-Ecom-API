package com.narturebelle.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** Targets exactly one of a Provider or a Booking (DB check constraint enforces this). expiresAt is always required — no indefinite shares. */
@Entity
@Table(name = "record_shares")
public class RecordShare {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long recordId;

    @Column(nullable = false)
    private Long grantedByUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ShareTargetType shareTargetType;

    private Long targetProviderId;

    private Long targetBookingId;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    private LocalDateTime revokedAt;

    private Long revokedByUserId;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }

    public RecordShare() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getRecordId() { return recordId; }
    public void setRecordId(Long recordId) { this.recordId = recordId; }

    public Long getGrantedByUserId() { return grantedByUserId; }
    public void setGrantedByUserId(Long grantedByUserId) { this.grantedByUserId = grantedByUserId; }

    public ShareTargetType getShareTargetType() { return shareTargetType; }
    public void setShareTargetType(ShareTargetType shareTargetType) { this.shareTargetType = shareTargetType; }

    public Long getTargetProviderId() { return targetProviderId; }
    public void setTargetProviderId(Long targetProviderId) { this.targetProviderId = targetProviderId; }

    public Long getTargetBookingId() { return targetBookingId; }
    public void setTargetBookingId(Long targetBookingId) { this.targetBookingId = targetBookingId; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }

    public LocalDateTime getRevokedAt() { return revokedAt; }
    public void setRevokedAt(LocalDateTime revokedAt) { this.revokedAt = revokedAt; }

    public Long getRevokedByUserId() { return revokedByUserId; }
    public void setRevokedByUserId(Long revokedByUserId) { this.revokedByUserId = revokedByUserId; }
}
