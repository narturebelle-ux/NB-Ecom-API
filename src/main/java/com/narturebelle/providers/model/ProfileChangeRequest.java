package com.narturebelle.providers.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** A live professional's requested change. The old details stay in place until it is approved. */
@Entity
@Table(schema = "provider_onboarding", name = "profile_change_requests")
public class ProfileChangeRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long applicationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProfileChangeType changeType;

    @Column(nullable = false, columnDefinition = "text")
    private String payloadEncrypted;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProfileChangeStatus status;

    private Long reviewerUserId;

    @Column(columnDefinition = "text")
    private String decisionReason;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime decidedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (this.createdAt == null) this.createdAt = now;
    }

    public ProfileChangeRequest() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getApplicationId() { return applicationId; }
    public void setApplicationId(Long applicationId) { this.applicationId = applicationId; }

    public ProfileChangeType getChangeType() { return changeType; }
    public void setChangeType(ProfileChangeType changeType) { this.changeType = changeType; }

    public String getPayloadEncrypted() { return payloadEncrypted; }
    public void setPayloadEncrypted(String payloadEncrypted) { this.payloadEncrypted = payloadEncrypted; }

    public ProfileChangeStatus getStatus() { return status; }
    public void setStatus(ProfileChangeStatus status) { this.status = status; }

    public Long getReviewerUserId() { return reviewerUserId; }
    public void setReviewerUserId(Long reviewerUserId) { this.reviewerUserId = reviewerUserId; }

    public String getDecisionReason() { return decisionReason; }
    public void setDecisionReason(String decisionReason) { this.decisionReason = decisionReason; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getDecidedAt() { return decidedAt; }
    public void setDecidedAt(LocalDateTime decidedAt) { this.decidedAt = decidedAt; }
}
