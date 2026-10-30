package com.narturebelle.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class SignOnboardingDocumentRequest {

    @NotNull
    private Long documentId;

    @NotBlank
    private String typedName;

    public Long getDocumentId() { return documentId; }
    public void setDocumentId(Long documentId) { this.documentId = documentId; }

    public String getTypedName() { return typedName; }
    public void setTypedName(String typedName) { this.typedName = typedName; }
}
