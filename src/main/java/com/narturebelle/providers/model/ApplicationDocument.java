package com.narturebelle.providers.model;

import com.narturebelle.model.FileKind;
import com.narturebelle.model.ScanVerdict;
import com.narturebelle.storage.StorageBackend;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * An uploaded file's details. Older files keep their bytes in ApplicationDocumentContent (so listing never
 * loads them); files uploaded straight to S3 (owner, 12 Nov 2026) have storageProvider S3 and no content row.
 */
@Entity
@Table(schema = "provider_onboarding", name = "application_documents")
public class ApplicationDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long applicationId;

    @Column(nullable = false, length = 80)
    private String questionKey;

    @Column(nullable = false)
    private String fileName;

    @Column(nullable = false, length = 100)
    private String contentType;

    @Column(nullable = false)
    private int sizeBytes;

    @Column(nullable = false)
    private LocalDateTime uploadedAt;

    /** S3 for files uploaded straight to S3 (owner, 12 Nov 2026); null for older files kept in Postgres. */
    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private StorageBackend storageProvider;

    private String storageBucket;

    @Column(length = 1000)
    private String storageObjectPath;

    /** GuardDuty's verdict for S3 files; null for Postgres files. */
    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private ScanVerdict scanStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private FileKind kind = FileKind.DOCUMENT;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (this.uploadedAt == null) this.uploadedAt = now;
    }

    public ApplicationDocument() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getApplicationId() { return applicationId; }
    public void setApplicationId(Long applicationId) { this.applicationId = applicationId; }

    public String getQuestionKey() { return questionKey; }
    public void setQuestionKey(String questionKey) { this.questionKey = questionKey; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }

    public int getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(int sizeBytes) { this.sizeBytes = sizeBytes; }

    public LocalDateTime getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(LocalDateTime uploadedAt) { this.uploadedAt = uploadedAt; }

    public StorageBackend getStorageProvider() { return storageProvider; }
    public void setStorageProvider(StorageBackend storageProvider) { this.storageProvider = storageProvider; }

    public String getStorageBucket() { return storageBucket; }
    public void setStorageBucket(String storageBucket) { this.storageBucket = storageBucket; }

    public String getStorageObjectPath() { return storageObjectPath; }
    public void setStorageObjectPath(String storageObjectPath) { this.storageObjectPath = storageObjectPath; }

    public ScanVerdict getScanStatus() { return scanStatus; }
    public void setScanStatus(ScanVerdict scanStatus) { this.scanStatus = scanStatus; }

    public FileKind getKind() { return kind; }
    public void setKind(FileKind kind) { this.kind = kind; }

    public boolean isOnS3() { return storageProvider == StorageBackend.S3; }
}
