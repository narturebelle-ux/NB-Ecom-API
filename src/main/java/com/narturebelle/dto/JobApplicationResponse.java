package com.narturebelle.dto;

import com.narturebelle.model.JobApplication;
import com.narturebelle.model.ScanVerdict;

import java.time.LocalDateTime;

/**
 * Admin view of one application. adminNotified is false while email is log-only or a send failed.
 * resumeScanStatus is null for older résumés kept in Postgres, else the S3 malware scan's verdict.
 */
public record JobApplicationResponse(Long id, Long jobId, String fullName, String email, String phone, String location,
                                     String linkedinUrl, String portfolioUrl, Integer yearsExperience, String coverNote,
                                     String resumeFileName, long resumeSizeBytes, LocalDateTime submittedAt,
                                     boolean adminNotified, ScanVerdict resumeScanStatus) {

    public static JobApplicationResponse from(JobApplication a) {
        return new JobApplicationResponse(a.getId(), a.getJobId(), a.getFullName(), a.getEmail(), a.getPhone(),
                a.getLocation(), a.getLinkedinUrl(), a.getPortfolioUrl(), a.getYearsExperience(), a.getCoverNote(),
                a.getResumeFileName(), a.getResumeSizeBytes(), a.getSubmittedAt(), a.getAdminNotifiedAt() != null,
                a.getResumeScanStatus());
    }
}
