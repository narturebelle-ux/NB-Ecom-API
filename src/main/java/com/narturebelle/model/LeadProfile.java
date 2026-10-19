package com.narturebelle.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "lead_profiles")
public class LeadProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private String email;

    @Column(nullable = false)
    private String phone;

    @Enumerated(EnumType.STRING)
    private PregnancyStage stage;

    private LocalDate dueDate;

    private String city;

    private String pincode;

    private String preferredHospital;

    @ElementCollection
    @CollectionTable(name = "lead_symptoms", joinColumns = @JoinColumn(name = "lead_id"))
    @Column(name = "symptom")
    private List<String> reportedSymptoms = new ArrayList<>();

    private String leadSource; // e.g. "DOCTOR_CHECKLIST", "ASSESSMENT_TOOL", "LIVE_WEBINAR"

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public LeadProfile() {}

    public LeadProfile(Long id, String name, String email, String phone, PregnancyStage stage,
                       LocalDate dueDate, String city, String pincode, String preferredHospital,
                       List<String> reportedSymptoms, String leadSource, LocalDateTime createdAt) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.stage = stage;
        this.dueDate = dueDate;
        this.city = city;
        this.pincode = pincode;
        this.preferredHospital = preferredHospital;
        this.reportedSymptoms = reportedSymptoms;
        this.leadSource = leadSource;
        this.createdAt = createdAt;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public PregnancyStage getStage() { return stage; }
    public void setStage(PregnancyStage stage) { this.stage = stage; }

    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getPincode() { return pincode; }
    public void setPincode(String pincode) { this.pincode = pincode; }

    public String getPreferredHospital() { return preferredHospital; }
    public void setPreferredHospital(String preferredHospital) { this.preferredHospital = preferredHospital; }

    public List<String> getReportedSymptoms() { return reportedSymptoms; }
    public void setReportedSymptoms(List<String> reportedSymptoms) { this.reportedSymptoms = reportedSymptoms; }

    public String getLeadSource() { return leadSource; }
    public void setLeadSource(String leadSource) { this.leadSource = leadSource; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
