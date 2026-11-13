package com.narturebelle.model;

import com.narturebelle.storage.StorageBackend;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * An S3 upload link handed out for a provider document or a careers résumé, not yet saved. It ties the
 * random object path to who asked (the signed-in user, or for careers a secret upload token) and to what
 * it is for (purpose, plus an application, change request or job id and an optional question key).
 * Kept apart from pending_uploads on purpose, so such a file can never be saved as a health record.
 * Removed when the file is saved or the upload is cancelled; an hourly sweep removes stale ones and their files.
 */
@Entity
@Table(name = "upload_intents", indexes = @Index(name = "idx_upload_intents_expires_at", columnList = "expiresAt"))
public class UploadIntent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private UploadPurpose purpose;

    /** Null for careers, which has no sign-in. */
    private Long ownerUserId;

    /** Provider application id, change request id or job id. */
    @Column(nullable = false)
    private Long contextId;

    /** The application question key, if any. */
    @Column(length = 80)
    private String contextKey;

    /** SHA-256 of the upload token, hex. Only set for careers. */
    @Column(length = 64)
    private String tokenHash;

    @Column(nullable = false, unique = true, length = 1000)
    private String objectPath;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private StorageBackend storageProvider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private FileKind kind;

    @Column(nullable = false)
    private String mimeType;

    @Column(nullable = false)
    private Long declaredSizeBytes;

    /** S3 multipart upload ID; null for a single PUT. */
    @Column(length = 1024)
    private String multipartUploadId;

    private Integer partCount;

    private LocalDateTime completedAt;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) this.createdAt = LocalDateTime.now();
    }

    public boolean isMultipart() { return multipartUploadId != null; }

    public Long getId() { return id; }
    public UploadPurpose getPurpose() { return purpose; }
    public void setPurpose(UploadPurpose purpose) { this.purpose = purpose; }
    public Long getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }
    public Long getContextId() { return contextId; }
    public void setContextId(Long contextId) { this.contextId = contextId; }
    public String getContextKey() { return contextKey; }
    public void setContextKey(String contextKey) { this.contextKey = contextKey; }
    public String getTokenHash() { return tokenHash; }
    public void setTokenHash(String tokenHash) { this.tokenHash = tokenHash; }
    public String getObjectPath() { return objectPath; }
    public void setObjectPath(String objectPath) { this.objectPath = objectPath; }
    public StorageBackend getStorageProvider() { return storageProvider; }
    public void setStorageProvider(StorageBackend storageProvider) { this.storageProvider = storageProvider; }
    public FileKind getKind() { return kind; }
    public void setKind(FileKind kind) { this.kind = kind; }
    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }
    public Long getDeclaredSizeBytes() { return declaredSizeBytes; }
    public void setDeclaredSizeBytes(Long declaredSizeBytes) { this.declaredSizeBytes = declaredSizeBytes; }
    public String getMultipartUploadId() { return multipartUploadId; }
    public void setMultipartUploadId(String multipartUploadId) { this.multipartUploadId = multipartUploadId; }
    public Integer getPartCount() { return partCount; }
    public void setPartCount(Integer partCount) { this.partCount = partCount; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
