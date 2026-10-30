package com.narturebelle.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** Someone applying for a JobPosting. The résumé bytes live in JobApplicationResume so listing
 * applications never loads them. Personal data -- retention is an open product-owner decision. */
@Entity
@Table(name = "job_applications")
public class JobApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long jobId;

    @Column(nullable = false, length = 120)
    private String fullName;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(length = 120)
    private String location;

    @Column(length = 300)
    private String linkedinUrl;

    @Column(length = 300)
    private String portfolioUrl;

    private Integer yearsExperience;

    @Column(length = 3000)
    private String coverNote;

    @Column(nullable = false, length = 150)
    private String resumeFileName;

    @Column(nullable = false, length = 100)
    private String resumeContentType;

    private long resumeSizeBytes;

    @Column(nullable = false)
    private LocalDateTime submittedAt;

    /** Null until the admin email actually went out (stays null while email is log-only). */
    private LocalDateTime adminNotifiedAt;

    @PrePersist
    protected void onCreate() {
        if (this.submittedAt == null) {
            this.submittedAt = LocalDateTime.now();
        }
    }

    public JobApplication() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getJobId() { return jobId; }
    public void setJobId(Long jobId) { this.jobId = jobId; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getLinkedinUrl() { return linkedinUrl; }
    public void setLinkedinUrl(String linkedinUrl) { this.linkedinUrl = linkedinUrl; }

    public String getPortfolioUrl() { return portfolioUrl; }
    public void setPortfolioUrl(String portfolioUrl) { this.portfolioUrl = portfolioUrl; }

    public Integer getYearsExperience() { return yearsExperience; }
    public void setYearsExperience(Integer yearsExperience) { this.yearsExperience = yearsExperience; }

    public String getCoverNote() { return coverNote; }
    public void setCoverNote(String coverNote) { this.coverNote = coverNote; }

    public String getResumeFileName() { return resumeFileName; }
    public void setResumeFileName(String resumeFileName) { this.resumeFileName = resumeFileName; }

    public String getResumeContentType() { return resumeContentType; }
    public void setResumeContentType(String resumeContentType) { this.resumeContentType = resumeContentType; }

    public long getResumeSizeBytes() { return resumeSizeBytes; }
    public void setResumeSizeBytes(long resumeSizeBytes) { this.resumeSizeBytes = resumeSizeBytes; }

    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }

    public LocalDateTime getAdminNotifiedAt() { return adminNotifiedAt; }
    public void setAdminNotifiedAt(LocalDateTime adminNotifiedAt) { this.adminNotifiedAt = adminNotifiedAt; }
}
