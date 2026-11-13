package com.narturebelle.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Bodies for the provider and careers direct-to-S3 uploads (same shapes as /records §10a). */
public final class DirectUploadRequests {

    private DirectUploadRequests() {}

    /**
     * POST .../files/upload-url. questionKey is used by /provider-application/files, changeRequestId by
     * /provider-workspace/files; careers takes the job from the path.
     */
    public record UploadUrl(@NotBlank @Size(max = 255) String fileName,
                            @NotBlank @Size(max = 255) String mimeType,
                            @NotNull @Positive Long sizeBytes,
                            @Size(max = 80) String questionKey,
                            Long changeRequestId) {}

    /** The save call's file part, sent once the browser has uploaded to S3. uploadToken is required for careers. */
    public record SavedFile(@NotBlank @Size(max = 1000) String objectPath,
                            @NotBlank @Size(max = 255) String originalFileName,
                            @NotBlank @Size(max = 255) String mimeType,
                            @NotNull @Positive Long sizeBytes,
                            @Size(max = 200) String uploadToken) {}
}
