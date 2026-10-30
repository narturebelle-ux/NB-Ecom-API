package com.narturebelle.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** A grant record. Never updated once created - revoking sets revokedAt; re-granting inserts a new row. */
@Entity
@Table(name = "consents")
public class Consent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConsentDocumentType documentType;

    @Column(nullable = false)
    private Integer documentVersion;

    private LocalDateTime grantedAt;

    private LocalDateTime revokedAt;

    @PrePersist
    protected void onCreate() {
        if (this.grantedAt == null) {
            this.grantedAt = LocalDateTime.now();
        }
    }

    public Consent() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public ConsentDocumentType getDocumentType() { return documentType; }
    public void setDocumentType(ConsentDocumentType documentType) { this.documentType = documentType; }

    public Integer getDocumentVersion() { return documentVersion; }
    public void setDocumentVersion(Integer documentVersion) { this.documentVersion = documentVersion; }

    public LocalDateTime getGrantedAt() { return grantedAt; }
    public void setGrantedAt(LocalDateTime grantedAt) { this.grantedAt = grantedAt; }

    public LocalDateTime getRevokedAt() { return revokedAt; }
    public void setRevokedAt(LocalDateTime revokedAt) { this.revokedAt = revokedAt; }
}
