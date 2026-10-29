package com.narturebelle.dto;

import com.narturebelle.model.RecordCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public class CreateRecordUploadUrlRequest {

    @NotNull
    private RecordCategory category;

    @NotBlank
    private String fileName;

    @NotBlank
    private String mimeType;

    @NotNull
    @Positive
    private Long sizeBytes;

    public RecordCategory getCategory() { return category; }
    public void setCategory(RecordCategory category) { this.category = category; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }

    public Long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(Long sizeBytes) { this.sizeBytes = sizeBytes; }
}
