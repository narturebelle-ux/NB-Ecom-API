package com.narturebelle.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * A user's request to delete their account. Nothing is erased automatically: retention and deletion
 * durations are still an open product-owner decision (roadmap Phase 4 "Still needed"), and some records
 * (payments, invoices) may have to be kept by law. An admin processes each request by hand and moves
 * its status along. At most one open (REQUESTED/IN_PROGRESS) request per user -- partial unique index in V20.
 */
@Entity
@Table(name = "account_deletion_requests")
public class AccountDeletionRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeletionRequestStatus status;

    @Column(nullable = false)
    private LocalDateTime requestedAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (this.requestedAt == null) {
            this.requestedAt = now;
        }
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public AccountDeletionRequest() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public DeletionRequestStatus getStatus() { return status; }
    public void setStatus(DeletionRequestStatus status) { this.status = status; }

    public LocalDateTime getRequestedAt() { return requestedAt; }
    public void setRequestedAt(LocalDateTime requestedAt) { this.requestedAt = requestedAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
