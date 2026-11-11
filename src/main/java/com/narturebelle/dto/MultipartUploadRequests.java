package com.narturebelle.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

/** Request bodies for the large-video upload steps (F9-S19 item 5). */
public final class MultipartUploadRequests {

    private MultipartUploadRequests() {}

    public static class PartUrls {
        @NotBlank private String objectPath;
        @NotEmpty private List<Integer> partNumbers;

        public String getObjectPath() { return objectPath; }
        public void setObjectPath(String objectPath) { this.objectPath = objectPath; }
        public List<Integer> getPartNumbers() { return partNumbers; }
        public void setPartNumbers(List<Integer> partNumbers) { this.partNumbers = partNumbers; }
    }

    public static class Complete {
        @NotBlank private String objectPath;
        @NotEmpty @Valid private List<Part> parts;

        public String getObjectPath() { return objectPath; }
        public void setObjectPath(String objectPath) { this.objectPath = objectPath; }
        public List<Part> getParts() { return parts; }
        public void setParts(List<Part> parts) { this.parts = parts; }
    }

    public static class Part {
        @NotNull @Positive private Integer partNumber;
        @NotBlank private String etag;

        public Integer getPartNumber() { return partNumber; }
        public void setPartNumber(Integer partNumber) { this.partNumber = partNumber; }
        public String getEtag() { return etag; }
        public void setEtag(String etag) { this.etag = etag; }
    }

    /** Optional body for download-url: "inline" (default, plays in a video tag) or "attachment" (save to phone). */
    public static class Download {
        private String disposition;

        public String getDisposition() { return disposition; }
        public void setDisposition(String disposition) { this.disposition = disposition; }
    }

    /** Support restore from trash; the reason is audited, so no health details. */
    public static class Restore {
        @NotBlank private String reason;

        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }
}
