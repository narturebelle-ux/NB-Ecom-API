package com.narturebelle.dto;

import com.narturebelle.model.FileKind;
import com.narturebelle.model.HealthRecord;
import com.narturebelle.model.RecordCategory;
import com.narturebelle.model.RecordStatus;
import com.narturebelle.model.ScanVerdict;
import com.narturebelle.service.RecordService.RecordState;

import java.time.LocalDateTime;

/**
 * available: false until an S3 file is scanned clean, and once a video is in trash. download-url refuses
 * with 409 while it's false. removalAt / warnFrom / trashedAt / permanentDeletionAt are for videos only (D49).
 */
public class HealthRecordResponse {

    private Long id;
    private RecordCategory category;
    private String originalFileName;
    private String mimeType;
    private Long sizeBytes;
    private RecordStatus status;
    private ScanVerdict scanStatus;
    private FileKind kind;
    private boolean available;
    private LocalDateTime removalAt;
    private LocalDateTime warnFrom;
    private LocalDateTime trashedAt;
    private LocalDateTime permanentDeletionAt;
    private LocalDateTime createdAt;

    public static HealthRecordResponse from(HealthRecord record, RecordState state) {
        HealthRecordResponse dto = new HealthRecordResponse();
        dto.id = record.getId();
        dto.category = record.getCategory();
        dto.originalFileName = record.getOriginalFileName();
        dto.mimeType = record.getMimeType();
        dto.sizeBytes = record.getSizeBytes();
        dto.status = record.getStatus();
        dto.scanStatus = record.getScanStatus();
        dto.kind = record.getKind();
        dto.available = state.available();
        dto.removalAt = state.removalAt();
        dto.warnFrom = state.warnFrom();
        dto.trashedAt = state.trashedAt();
        dto.permanentDeletionAt = state.permanentDeletionAt();
        dto.createdAt = record.getCreatedAt();
        return dto;
    }

    public Long getId() { return id; }
    public RecordCategory getCategory() { return category; }
    public String getOriginalFileName() { return originalFileName; }
    public String getMimeType() { return mimeType; }
    public Long getSizeBytes() { return sizeBytes; }
    public RecordStatus getStatus() { return status; }
    public ScanVerdict getScanStatus() { return scanStatus; }
    public FileKind getKind() { return kind; }
    public boolean isAvailable() { return available; }
    public LocalDateTime getRemovalAt() { return removalAt; }
    public LocalDateTime getWarnFrom() { return warnFrom; }
    public LocalDateTime getTrashedAt() { return trashedAt; }
    public LocalDateTime getPermanentDeletionAt() { return permanentDeletionAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
