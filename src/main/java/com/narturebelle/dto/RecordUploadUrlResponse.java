package com.narturebelle.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.narturebelle.service.RecordService.RecordUploadIntent;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * mode SINGLE: PUT the file to uploadUrl with the given headers (Content-Type on S3). token is only set
 * for Supabase. mode MULTIPART (large videos on S3): ask for part links, PUT each part, then complete.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RecordUploadUrlResponse {

    private String mode;
    private String objectPath;
    private String uploadUrl;
    private String token;
    private LocalDateTime expiresAt;
    private Map<String, String> headers;
    private String uploadId;
    private Long partSizeBytes;
    private Integer partCount;

    public static RecordUploadUrlResponse of(String objectPath, String uploadUrl, String token, LocalDateTime expiresAt) {
        RecordUploadUrlResponse dto = new RecordUploadUrlResponse();
        dto.mode = "SINGLE";
        dto.objectPath = objectPath;
        dto.uploadUrl = uploadUrl;
        dto.token = token;
        dto.expiresAt = expiresAt;
        return dto;
    }

    public static RecordUploadUrlResponse from(RecordUploadIntent intent) {
        RecordUploadUrlResponse dto = new RecordUploadUrlResponse();
        dto.mode = intent.mode();
        dto.objectPath = intent.objectPath();
        dto.uploadUrl = intent.uploadUrl();
        dto.token = intent.token();
        dto.expiresAt = intent.expiresAt();
        dto.headers = intent.headers();
        dto.uploadId = intent.uploadId();
        dto.partSizeBytes = intent.partSizeBytes();
        dto.partCount = intent.partCount();
        return dto;
    }

    public String getMode() { return mode; }
    public String getObjectPath() { return objectPath; }
    public String getUploadUrl() { return uploadUrl; }
    public String getToken() { return token; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public Map<String, String> getHeaders() { return headers; }
    public String getUploadId() { return uploadId; }
    public Long getPartSizeBytes() { return partSizeBytes; }
    public Integer getPartCount() { return partCount; }
}
