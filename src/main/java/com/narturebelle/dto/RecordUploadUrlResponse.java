package com.narturebelle.dto;

import java.time.LocalDateTime;

public class RecordUploadUrlResponse {

    private String objectPath;
    private String uploadUrl;
    private String token;
    private LocalDateTime expiresAt;

    public static RecordUploadUrlResponse of(String objectPath, String uploadUrl, String token, LocalDateTime expiresAt) {
        RecordUploadUrlResponse dto = new RecordUploadUrlResponse();
        dto.objectPath = objectPath;
        dto.uploadUrl = uploadUrl;
        dto.token = token;
        dto.expiresAt = expiresAt;
        return dto;
    }

    public String getObjectPath() { return objectPath; }
    public String getUploadUrl() { return uploadUrl; }
    public String getToken() { return token; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
}
