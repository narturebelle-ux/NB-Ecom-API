package com.narturebelle.providers.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** Payout details, collected only after approval. The account number is stored encrypted. */
@Entity
@Table(schema = "provider_onboarding", name = "provider_bank_details")
public class ProviderBankDetails {

    @Id
    private Long applicationId;

    @Column(nullable = false, length = 160)
    private String accountHolderName;

    @Column(nullable = false, columnDefinition = "text")
    private String accountNumberEncrypted;

    @Column(nullable = false, length = 4)
    private String accountLast4;

    @Column(nullable = false, length = 11)
    private String ifsc;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BankAccountType accountType;

    @Column(nullable = false)
    private boolean gstRegistered;

    @Column(length = 15)
    private String gstin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BankVerificationStatus verificationStatus;

    private LocalDateTime verifiedAt;

    private Long verifiedByUserId;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public ProviderBankDetails() {}

    public Long getApplicationId() { return applicationId; }
    public void setApplicationId(Long applicationId) { this.applicationId = applicationId; }

    public String getAccountHolderName() { return accountHolderName; }
    public void setAccountHolderName(String accountHolderName) { this.accountHolderName = accountHolderName; }

    public String getAccountNumberEncrypted() { return accountNumberEncrypted; }
    public void setAccountNumberEncrypted(String accountNumberEncrypted) { this.accountNumberEncrypted = accountNumberEncrypted; }

    public String getAccountLast4() { return accountLast4; }
    public void setAccountLast4(String accountLast4) { this.accountLast4 = accountLast4; }

    public String getIfsc() { return ifsc; }
    public void setIfsc(String ifsc) { this.ifsc = ifsc; }

    public BankAccountType getAccountType() { return accountType; }
    public void setAccountType(BankAccountType accountType) { this.accountType = accountType; }

    public boolean isGstRegistered() { return gstRegistered; }
    public void setGstRegistered(boolean gstRegistered) { this.gstRegistered = gstRegistered; }

    public String getGstin() { return gstin; }
    public void setGstin(String gstin) { this.gstin = gstin; }

    public BankVerificationStatus getVerificationStatus() { return verificationStatus; }
    public void setVerificationStatus(BankVerificationStatus verificationStatus) { this.verificationStatus = verificationStatus; }

    public LocalDateTime getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(LocalDateTime verifiedAt) { this.verifiedAt = verifiedAt; }

    public Long getVerifiedByUserId() { return verifiedByUserId; }
    public void setVerifiedByUserId(Long verifiedByUserId) { this.verifiedByUserId = verifiedByUserId; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
