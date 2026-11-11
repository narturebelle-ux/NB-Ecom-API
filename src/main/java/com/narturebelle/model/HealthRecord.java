package com.narturebelle.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Metadata only — the actual file bytes live in Supabase Storage or AWS S3 (storageProvider), never
 * here. storageObjectPath is a random key (no filename in it) so the storage path itself never leaks
 * the document name into logs/traces. Supabase files stay NOT_SCANNED (NoOpMalwareScanner); S3 files
 * are PENDING until GuardDuty tags them. removalAt is set for videos only: the moment the S3 lifecycle
 * rule moves them to trash (D49).
 */
@Entity
@Table(name = "health_records")
public class HealthRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RecordCategory category;

    @Column(nullable = false, length = 500)
    private String originalFileName;

    private String mimeType;

    private Long sizeBytes;

    @Column(nullable = false)
    private String storageBucket;

    @Column(nullable = false, unique = true, length = 1000)
    private String storageObjectPath;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RecordStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ScanVerdict scanStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private com.narturebelle.storage.StorageBackend storageProvider = com.narturebelle.storage.StorageBackend.SUPABASE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private FileKind kind = FileKind.DOCUMENT;

    private LocalDateTime removalAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (this.createdAt == null) this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public HealthRecord() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public RecordCategory getCategory() { return category; }
    public void setCategory(RecordCategory category) { this.category = category; }

    public String getOriginalFileName() { return originalFileName; }
    public void setOriginalFileName(String originalFileName) { this.originalFileName = originalFileName; }

    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }

    public Long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(Long sizeBytes) { this.sizeBytes = sizeBytes; }

    public String getStorageBucket() { return storageBucket; }
    public void setStorageBucket(String storageBucket) { this.storageBucket = storageBucket; }

    public String getStorageObjectPath() { return storageObjectPath; }
    public void setStorageObjectPath(String storageObjectPath) { this.storageObjectPath = storageObjectPath; }

    public RecordStatus getStatus() { return status; }
    public void setStatus(RecordStatus status) { this.status = status; }

    public ScanVerdict getScanStatus() { return scanStatus; }
    public void setScanStatus(ScanVerdict scanStatus) { this.scanStatus = scanStatus; }

    public com.narturebelle.storage.StorageBackend getStorageProvider() { return storageProvider; }
    public void setStorageProvider(com.narturebelle.storage.StorageBackend storageProvider) { this.storageProvider = storageProvider; }

    public FileKind getKind() { return kind; }
    public void setKind(FileKind kind) { this.kind = kind; }

    public LocalDateTime getRemovalAt() { return removalAt; }
    public void setRemovalAt(LocalDateTime removalAt) { this.removalAt = removalAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
