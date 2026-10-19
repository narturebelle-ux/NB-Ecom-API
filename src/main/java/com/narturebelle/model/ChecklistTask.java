package com.narturebelle.model;

import jakarta.persistence.*;

@Entity
@Table(name = "checklist_tasks")
public class ChecklistTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PregnancyStage stage;

    @Column(nullable = false)
    private String testName; // e.g. "NT Scan & Double Marker Test", "Anomaly Scan", "Glucose Screening"

    private String weeksDue; // e.g. "Weeks 11-13", "Weeks 18-22", "Weeks 36+"

    @Column(length = 1500)
    private String description;

    private Boolean isMandatoryMedicalTest;

    private String category; // e.g., "Doctor Question", "Ultrasound Scan", "Blood Test", "Hospital Prep"

    private String recommendedFacilityType; // e.g., "Fetal Medicine Scan Center", "Diagnostic Blood Lab", "Maternity Hospital"

    // Sponsored Healthcare Partner Ad placement
    private String sponsoredPartnerName;
    private String sponsoredPartnerBadge; // e.g., "NABH Accredited Partner", "15% NartureBelle Mother Discount"
    private String partnerDiscountCode;
    private String partnerBookingUrl;

    public ChecklistTask() {}

    public ChecklistTask(Long id, PregnancyStage stage, String testName, String weeksDue, String description,
                         Boolean isMandatoryMedicalTest, String category, String recommendedFacilityType,
                         String sponsoredPartnerName, String sponsoredPartnerBadge, String partnerDiscountCode,
                         String partnerBookingUrl) {
        this.id = id;
        this.stage = stage;
        this.testName = testName;
        this.weeksDue = weeksDue;
        this.description = description;
        this.isMandatoryMedicalTest = isMandatoryMedicalTest;
        this.category = category;
        this.recommendedFacilityType = recommendedFacilityType;
        this.sponsoredPartnerName = sponsoredPartnerName;
        this.sponsoredPartnerBadge = sponsoredPartnerBadge;
        this.partnerDiscountCode = partnerDiscountCode;
        this.partnerBookingUrl = partnerBookingUrl;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public PregnancyStage getStage() { return stage; }
    public void setStage(PregnancyStage stage) { this.stage = stage; }

    public String getTestName() { return testName; }
    public void setTestName(String testName) { this.testName = testName; }

    public String getWeeksDue() { return weeksDue; }
    public void setWeeksDue(String weeksDue) { this.weeksDue = weeksDue; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Boolean getIsMandatoryMedicalTest() { return isMandatoryMedicalTest; }
    public void setIsMandatoryMedicalTest(Boolean isMandatoryMedicalTest) { this.isMandatoryMedicalTest = isMandatoryMedicalTest; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getRecommendedFacilityType() { return recommendedFacilityType; }
    public void setRecommendedFacilityType(String recommendedFacilityType) { this.recommendedFacilityType = recommendedFacilityType; }

    public String getSponsoredPartnerName() { return sponsoredPartnerName; }
    public void setSponsoredPartnerName(String sponsoredPartnerName) { this.sponsoredPartnerName = sponsoredPartnerName; }

    public String getSponsoredPartnerBadge() { return sponsoredPartnerBadge; }
    public void setSponsoredPartnerBadge(String sponsoredPartnerBadge) { this.sponsoredPartnerBadge = sponsoredPartnerBadge; }

    public String getPartnerDiscountCode() { return partnerDiscountCode; }
    public void setPartnerDiscountCode(String partnerDiscountCode) { this.partnerDiscountCode = partnerDiscountCode; }

    public String getPartnerBookingUrl() { return partnerBookingUrl; }
    public void setPartnerBookingUrl(String partnerBookingUrl) { this.partnerBookingUrl = partnerBookingUrl; }
}
