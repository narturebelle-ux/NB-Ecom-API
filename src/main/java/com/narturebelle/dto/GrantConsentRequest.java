package com.narturebelle.dto;

import com.narturebelle.model.ConsentDocumentType;
import jakarta.validation.constraints.NotNull;

public class GrantConsentRequest {

    @NotNull
    private ConsentDocumentType documentType;

    @NotNull
    private Integer documentVersion;

    public ConsentDocumentType getDocumentType() { return documentType; }
    public void setDocumentType(ConsentDocumentType documentType) { this.documentType = documentType; }

    public Integer getDocumentVersion() { return documentVersion; }
    public void setDocumentVersion(Integer documentVersion) { this.documentVersion = documentVersion; }
}
