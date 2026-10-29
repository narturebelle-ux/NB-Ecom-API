package com.narturebelle.dto;

import com.narturebelle.model.HealthRecord;
import com.narturebelle.model.RecordCategory;
import com.narturebelle.model.RecordStatus;
import com.narturebelle.model.ScanVerdict;

import java.time.LocalDateTime;

public class HealthRecordResponse {

    private Long id;
    private RecordCategory category;
    private String originalFileName;
    private String mimeType;
    private Long sizeBytes;
    private RecordStatus status;
    private ScanVerdict scanStatus;
    private LocalDateTime createdAt;

    public static HealthRecordResponse from(HealthRecord record) {
        HealthRecordResponse dto = new HealthRecordResponse();
        dto.id = record.getId();
        dto.category = record.getCategory();
        dto.originalFileName = record.getOriginalFileName();
        dto.mimeType = record.getMimeType();
        dto.sizeBytes = record.getSizeBytes();
        dto.status = record.getStatus();
        dto.scanStatus = record.getScanStatus();
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
    public LocalDateTime getCreatedAt() { return createdAt; }
}
