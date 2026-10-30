package com.narturebelle.dto;

import java.time.LocalDateTime;

public class RecordDownloadUrlResponse {

    private String downloadUrl;
    private LocalDateTime expiresAt;

    public static RecordDownloadUrlResponse of(String downloadUrl, LocalDateTime expiresAt) {
        RecordDownloadUrlResponse dto = new RecordDownloadUrlResponse();
        dto.downloadUrl = downloadUrl;
        dto.expiresAt = expiresAt;
        return dto;
    }

    public String getDownloadUrl() { return downloadUrl; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
}
