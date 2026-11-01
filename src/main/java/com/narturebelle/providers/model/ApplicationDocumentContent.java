package com.narturebelle.providers.model;

import jakarta.persistence.*;

/** The file itself, in Postgres (bytea) like careers résumés. Only the applicant and reviewers can download it. */
@Entity
@Table(schema = "provider_onboarding", name = "application_document_contents")
public class ApplicationDocumentContent {

    @Id
    private Long documentId;

    @Column(nullable = false, length = 5 * 1024 * 1024)
    private byte[] content;

    public ApplicationDocumentContent() {}

    public ApplicationDocumentContent(Long documentId, byte[] content) {
        this.documentId = documentId;
        this.content = content;
    }

    public Long getDocumentId() { return documentId; }
    public void setDocumentId(Long documentId) { this.documentId = documentId; }

    public byte[] getContent() { return content; }
    public void setContent(byte[] content) { this.content = content; }
}
