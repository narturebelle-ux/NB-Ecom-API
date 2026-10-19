package com.narturebelle.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "nurse_bookings")
public class NurseBooking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String clientName;

    @Column(nullable = false)
    private String clientPhone;

    private String clientEmail;

    @Enumerated(EnumType.STRING)
    private PregnancyStage currentStage;

    private String preferredLanguage; // e.g. "English", "Hindi", "Telugu", "Tamil", "Kannada"

    private Long packageId;

    private String packageName;

    private String city;

    @Column(length = 1500)
    private String notes;

    private String status; // e.g. "PENDING_MATCHING", "NURSE_ASSIGNED", "ACTIVE"

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.status == null) {
            this.status = "PENDING_MATCHING";
        }
    }

    public NurseBooking() {}

    public NurseBooking(Long id, String clientName, String clientPhone, String clientEmail,
                        PregnancyStage currentStage, String preferredLanguage, Long packageId,
                        String packageName, String city, String notes, String status, LocalDateTime createdAt) {
        this.id = id;
        this.clientName = clientName;
        this.clientPhone = clientPhone;
        this.clientEmail = clientEmail;
        this.currentStage = currentStage;
        this.preferredLanguage = preferredLanguage;
        this.packageId = packageId;
        this.packageName = packageName;
        this.city = city;
        this.notes = notes;
        this.status = status;
        this.createdAt = createdAt;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getClientName() { return clientName; }
    public void setClientName(String clientName) { this.clientName = clientName; }

    public String getClientPhone() { return clientPhone; }
    public void setClientPhone(String clientPhone) { this.clientPhone = clientPhone; }

    public String getClientEmail() { return clientEmail; }
    public void setClientEmail(String clientEmail) { this.clientEmail = clientEmail; }

    public PregnancyStage getCurrentStage() { return currentStage; }
    public void setCurrentStage(PregnancyStage currentStage) { this.currentStage = currentStage; }

    public String getPreferredLanguage() { return preferredLanguage; }
    public void setPreferredLanguage(String preferredLanguage) { this.preferredLanguage = preferredLanguage; }

    public Long getPackageId() { return packageId; }
    public void setPackageId(Long packageId) { this.packageId = packageId; }

    public String getPackageName() { return packageName; }
    public void setPackageName(String packageName) { this.packageName = packageName; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
