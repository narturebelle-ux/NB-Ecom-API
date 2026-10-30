package com.narturebelle.dto;

import com.narturebelle.model.RecordShare;
import com.narturebelle.model.ShareTargetType;

import java.time.LocalDateTime;

public class RecordShareResponse {

    private Long id;
    private Long recordId;
    private ShareTargetType shareTargetType;
    private Long targetProviderId;
    private Long targetBookingId;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private LocalDateTime revokedAt;

    public static RecordShareResponse from(RecordShare share) {
        RecordShareResponse dto = new RecordShareResponse();
        dto.id = share.getId();
        dto.recordId = share.getRecordId();
        dto.shareTargetType = share.getShareTargetType();
        dto.targetProviderId = share.getTargetProviderId();
        dto.targetBookingId = share.getTargetBookingId();
        dto.createdAt = share.getCreatedAt();
        dto.expiresAt = share.getExpiresAt();
        dto.revokedAt = share.getRevokedAt();
        return dto;
    }

    public Long getId() { return id; }
    public Long getRecordId() { return recordId; }
    public ShareTargetType getShareTargetType() { return shareTargetType; }
    public Long getTargetProviderId() { return targetProviderId; }
    public Long getTargetBookingId() { return targetBookingId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public LocalDateTime getRevokedAt() { return revokedAt; }
}
