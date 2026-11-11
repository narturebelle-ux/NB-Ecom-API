package com.narturebelle.model;

import com.narturebelle.storage.StorageBackend;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * An upload link the API has handed out but whose record isn't created yet. It ties the random object
 * path to the mother who asked for it, so nobody else can register or finish her upload. Removed when
 * the record is created or the upload is cancelled.
 */
@Entity
@Table(name = "pending_uploads")
public class PendingUpload {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

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
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
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
