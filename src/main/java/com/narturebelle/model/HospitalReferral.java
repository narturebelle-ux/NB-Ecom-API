package com.narturebelle.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** A user's referral to a HospitalPartner, carrying a code the hospital quotes back so the visit can be
 * attributed to NartureBelle. At most one REQUESTED referral per (user, hospital) -- partial unique index
 * in V17 backstops the service's check. CONFIRMED is set by an admin once the hospital confirms. */
@Entity
@Table(name = "hospital_referrals")
public class HospitalReferral {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long hospitalId;

    @Column(nullable = false, unique = true)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReferralStatus status;

    @Column(length = 1000)
    private String note;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime confirmedAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }

    public HospitalReferral() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Long getHospitalId() { return hospitalId; }
    public void setHospitalId(Long hospitalId) { this.hospitalId = hospitalId; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public ReferralStatus getStatus() { return status; }
    public void setStatus(ReferralStatus status) { this.status = status; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getConfirmedAt() { return confirmedAt; }
    public void setConfirmedAt(LocalDateTime confirmedAt) { this.confirmedAt = confirmedAt; }
}
