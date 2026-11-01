package com.narturebelle.providers.model;

import com.narturebelle.model.Role;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** One application by one account, from first answer to live professional. Rejected, withdrawn and closed applications stay as history. */
@Entity
@Table(schema = "provider_onboarding", name = "provider_applications")
public class ProviderApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role professionalType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApplicationStatus status;

    @Column(nullable = false)
    private String termsVersion;

    @Column(nullable = false)
    private LocalDateTime termsAcceptedAt;

    private String registrationNumber;

    private LocalDate licenceExpiresOn;

    @Column(nullable = false)
    private boolean licenceLapsed;

    private LocalDateTime submittedAt;

    @Column(nullable = false)
    private LocalDateTime lastActivityAt;

    @Column(nullable = false)
    private int reminderCount;

    private LocalDateTime changesRequestedAt;

    private Long operationsReviewerId;

    private LocalDateTime operationsReviewedAt;

    private Long medicalReviewerId;

    private LocalDateTime medicalReviewedAt;

    private Integer confirmedExperienceYears;

    @Column(length = 500)
    private String clearedStages;

    @Column(nullable = false)
    private boolean canPrescribe;

    @Column(columnDefinition = "text")
    private String rejectionReason;

    @Column(nullable = false)
    private boolean fraudBlocked;

    private LocalDate reapplyAfter;

    private LocalDateTime closedAt;

    private LocalDateTime purgedAt;

    private Long providerId;

    private LocalDateTime verifiedAt;

    @Column(nullable = false)
    private boolean recheckRequired;

    private LocalDateTime activatedAt;

    private String agreementVersion;

    private String agreementTypedName;

    private LocalDateTime agreementSignedAt;

    private LocalDateTime trainingPassedAt;

    private LocalDateTime profileConfirmedAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Version
    private Long version;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (this.createdAt == null) this.createdAt = now;
        if (this.lastActivityAt == null) this.lastActivityAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public ProviderApplication() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Role getProfessionalType() { return professionalType; }
    public void setProfessionalType(Role professionalType) { this.professionalType = professionalType; }

    public ApplicationStatus getStatus() { return status; }
    public void setStatus(ApplicationStatus status) { this.status = status; }

    public String getTermsVersion() { return termsVersion; }
    public void setTermsVersion(String termsVersion) { this.termsVersion = termsVersion; }

    public LocalDateTime getTermsAcceptedAt() { return termsAcceptedAt; }
    public void setTermsAcceptedAt(LocalDateTime termsAcceptedAt) { this.termsAcceptedAt = termsAcceptedAt; }

    public String getRegistrationNumber() { return registrationNumber; }
    public void setRegistrationNumber(String registrationNumber) { this.registrationNumber = registrationNumber; }

    public LocalDate getLicenceExpiresOn() { return licenceExpiresOn; }
    public void setLicenceExpiresOn(LocalDate licenceExpiresOn) { this.licenceExpiresOn = licenceExpiresOn; }

    public boolean isLicenceLapsed() { return licenceLapsed; }
    public void setLicenceLapsed(boolean licenceLapsed) { this.licenceLapsed = licenceLapsed; }

    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }

    public LocalDateTime getLastActivityAt() { return lastActivityAt; }
    public void setLastActivityAt(LocalDateTime lastActivityAt) { this.lastActivityAt = lastActivityAt; }

    public int getReminderCount() { return reminderCount; }
    public void setReminderCount(int reminderCount) { this.reminderCount = reminderCount; }

    public LocalDateTime getChangesRequestedAt() { return changesRequestedAt; }
    public void setChangesRequestedAt(LocalDateTime changesRequestedAt) { this.changesRequestedAt = changesRequestedAt; }

    public Long getOperationsReviewerId() { return operationsReviewerId; }
    public void setOperationsReviewerId(Long operationsReviewerId) { this.operationsReviewerId = operationsReviewerId; }

    public LocalDateTime getOperationsReviewedAt() { return operationsReviewedAt; }
    public void setOperationsReviewedAt(LocalDateTime operationsReviewedAt) { this.operationsReviewedAt = operationsReviewedAt; }

    public Long getMedicalReviewerId() { return medicalReviewerId; }
    public void setMedicalReviewerId(Long medicalReviewerId) { this.medicalReviewerId = medicalReviewerId; }

    public LocalDateTime getMedicalReviewedAt() { return medicalReviewedAt; }
    public void setMedicalReviewedAt(LocalDateTime medicalReviewedAt) { this.medicalReviewedAt = medicalReviewedAt; }

    public Integer getConfirmedExperienceYears() { return confirmedExperienceYears; }
    public void setConfirmedExperienceYears(Integer confirmedExperienceYears) { this.confirmedExperienceYears = confirmedExperienceYears; }

    public String getClearedStages() { return clearedStages; }
    public void setClearedStages(String clearedStages) { this.clearedStages = clearedStages; }

    public boolean isCanPrescribe() { return canPrescribe; }
    public void setCanPrescribe(boolean canPrescribe) { this.canPrescribe = canPrescribe; }

    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }

    public boolean isFraudBlocked() { return fraudBlocked; }
    public void setFraudBlocked(boolean fraudBlocked) { this.fraudBlocked = fraudBlocked; }

    public LocalDate getReapplyAfter() { return reapplyAfter; }
    public void setReapplyAfter(LocalDate reapplyAfter) { this.reapplyAfter = reapplyAfter; }

    public LocalDateTime getClosedAt() { return closedAt; }
    public void setClosedAt(LocalDateTime closedAt) { this.closedAt = closedAt; }

    public LocalDateTime getPurgedAt() { return purgedAt; }
    public void setPurgedAt(LocalDateTime purgedAt) { this.purgedAt = purgedAt; }

    public Long getProviderId() { return providerId; }
    public void setProviderId(Long providerId) { this.providerId = providerId; }

    public LocalDateTime getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(LocalDateTime verifiedAt) { this.verifiedAt = verifiedAt; }

    public boolean isRecheckRequired() { return recheckRequired; }
    public void setRecheckRequired(boolean recheckRequired) { this.recheckRequired = recheckRequired; }

    public LocalDateTime getActivatedAt() { return activatedAt; }
    public void setActivatedAt(LocalDateTime activatedAt) { this.activatedAt = activatedAt; }

    public String getAgreementVersion() { return agreementVersion; }
    public void setAgreementVersion(String agreementVersion) { this.agreementVersion = agreementVersion; }

    public String getAgreementTypedName() { return agreementTypedName; }
    public void setAgreementTypedName(String agreementTypedName) { this.agreementTypedName = agreementTypedName; }

    public LocalDateTime getAgreementSignedAt() { return agreementSignedAt; }
    public void setAgreementSignedAt(LocalDateTime agreementSignedAt) { this.agreementSignedAt = agreementSignedAt; }

    public LocalDateTime getTrainingPassedAt() { return trainingPassedAt; }
    public void setTrainingPassedAt(LocalDateTime trainingPassedAt) { this.trainingPassedAt = trainingPassedAt; }

    public LocalDateTime getProfileConfirmedAt() { return profileConfirmedAt; }
    public void setProfileConfirmedAt(LocalDateTime profileConfirmedAt) { this.profileConfirmedAt = profileConfirmedAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
