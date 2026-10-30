package com.narturebelle.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** One signature per (document, user) -- enforced by a DB unique constraint, same two-layer
 * idempotency style as everywhere else: the service checks first, the constraint backstops it. */
@Entity
@Table(name = "onboarding_document_signatures", uniqueConstraints = @UniqueConstraint(columnNames = {"document_id", "user_id"}))
public class OnboardingDocumentSignature {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long documentId;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String typedName;

    @Column(nullable = false)
    private LocalDateTime signedAt;

    private LocalDateTime emailedAt;

    @PrePersist
    protected void onCreate() {
        if (this.signedAt == null) {
            this.signedAt = LocalDateTime.now();
        }
    }

    public OnboardingDocumentSignature() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getDocumentId() { return documentId; }
    public void setDocumentId(Long documentId) { this.documentId = documentId; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getTypedName() { return typedName; }
    public void setTypedName(String typedName) { this.typedName = typedName; }

    public LocalDateTime getSignedAt() { return signedAt; }
    public void setSignedAt(LocalDateTime signedAt) { this.signedAt = signedAt; }

    public LocalDateTime getEmailedAt() { return emailedAt; }
    public void setEmailedAt(LocalDateTime emailedAt) { this.emailedAt = emailedAt; }
}
