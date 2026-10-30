package com.narturebelle.dto;

import com.narturebelle.model.OnboardingDocument;
import com.narturebelle.model.OnboardingDocumentType;

import java.time.LocalDateTime;

public class OnboardingDocumentResponse {

    private Long id;
    private OnboardingDocumentType type;
    private Integer version;
    private String title;
    private String bodyMarkdown;
    private LocalDateTime effectiveAt;
    private boolean signed;

    public static OnboardingDocumentResponse from(OnboardingDocument document, boolean signed) {
        OnboardingDocumentResponse dto = new OnboardingDocumentResponse();
        dto.id = document.getId();
        dto.type = document.getType();
        dto.version = document.getVersion();
        dto.title = document.getTitle();
        dto.bodyMarkdown = document.getBodyMarkdown();
        dto.effectiveAt = document.getEffectiveAt();
        dto.signed = signed;
        return dto;
    }

    public Long getId() { return id; }
    public OnboardingDocumentType getType() { return type; }
    public Integer getVersion() { return version; }
    public String getTitle() { return title; }
    public String getBodyMarkdown() { return bodyMarkdown; }
    public LocalDateTime getEffectiveAt() { return effectiveAt; }
    public boolean isSigned() { return signed; }
}
