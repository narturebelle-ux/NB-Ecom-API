package com.narturebelle.dto;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class SignOnboardingDocumentRequest {

    @NotNull
    private Long documentId;

    @NotBlank
    @Size(max = 255) private String typedName;

    public Long getDocumentId() { return documentId; }
    public void setDocumentId(Long documentId) { this.documentId = documentId; }

    public String getTypedName() { return typedName; }
    public void setTypedName(String typedName) { this.typedName = typedName; }
}
