package com.narturebelle.dto;

import jakarta.validation.constraints.Size;
import com.narturebelle.model.RecordCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public class RegisterRecordRequest {

    @NotNull
    private RecordCategory category;

    @NotBlank
    private String objectPath;

    @NotBlank
    @Size(max = 255) private String originalFileName;

    @NotBlank
    private String mimeType;

    @NotNull
    @Positive
    private Long sizeBytes;

    public RecordCategory getCategory() { return category; }
    public void setCategory(RecordCategory category) { this.category = category; }

    public String getObjectPath() { return objectPath; }
    public void setObjectPath(String objectPath) { this.objectPath = objectPath; }

    public String getOriginalFileName() { return originalFileName; }
    public void setOriginalFileName(String originalFileName) { this.originalFileName = originalFileName; }

    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }

    public Long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(Long sizeBytes) { this.sizeBytes = sizeBytes; }
}
