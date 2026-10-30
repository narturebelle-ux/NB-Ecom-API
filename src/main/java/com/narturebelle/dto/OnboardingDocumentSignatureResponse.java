package com.narturebelle.dto;

import com.narturebelle.model.OnboardingDocumentSignature;

import java.time.LocalDateTime;

public class OnboardingDocumentSignatureResponse {

    private Long id;
    private Long documentId;
    private String typedName;
    private LocalDateTime signedAt;

    public static OnboardingDocumentSignatureResponse from(OnboardingDocumentSignature signature) {
        OnboardingDocumentSignatureResponse dto = new OnboardingDocumentSignatureResponse();
        dto.id = signature.getId();
        dto.documentId = signature.getDocumentId();
        dto.typedName = signature.getTypedName();
        dto.signedAt = signature.getSignedAt();
        return dto;
    }

    public Long getId() { return id; }
    public Long getDocumentId() { return documentId; }
    public String getTypedName() { return typedName; }
    public LocalDateTime getSignedAt() { return signedAt; }
}
