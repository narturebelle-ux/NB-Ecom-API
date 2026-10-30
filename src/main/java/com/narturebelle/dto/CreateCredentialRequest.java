package com.narturebelle.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

public class CreateCredentialRequest {

    @NotBlank
    private String credentialType;

    private String documentReference;
    private LocalDate issuedAt;
    private LocalDate expiresAt;

    public String getCredentialType() { return credentialType; }
    public void setCredentialType(String credentialType) { this.credentialType = credentialType; }

    public String getDocumentReference() { return documentReference; }
    public void setDocumentReference(String documentReference) { this.documentReference = documentReference; }

    public LocalDate getIssuedAt() { return issuedAt; }
    public void setIssuedAt(LocalDate issuedAt) { this.issuedAt = issuedAt; }

    public LocalDate getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDate expiresAt) { this.expiresAt = expiresAt; }
}
