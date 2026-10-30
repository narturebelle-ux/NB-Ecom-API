package com.narturebelle.model;

import jakarta.persistence.*;

/** The résumé file itself, kept in Postgres (bytea) rather than Supabase Storage: storage credentials
 * aren't configured in prod yet, and résumés are capped at 5 MB. Only admins can download it. */
@Entity
@Table(name = "job_application_resumes")
public class JobApplicationResume {

    @Id
    private Long applicationId;

    @Column(nullable = false, length = 5 * 1024 * 1024)
    private byte[] content;

    public JobApplicationResume() {}

    public JobApplicationResume(Long applicationId, byte[] content) {
        this.applicationId = applicationId;
        this.content = content;
    }

    public Long getApplicationId() { return applicationId; }
    public void setApplicationId(Long applicationId) { this.applicationId = applicationId; }

    public byte[] getContent() { return content; }
    public void setContent(byte[] content) { this.content = content; }
}
