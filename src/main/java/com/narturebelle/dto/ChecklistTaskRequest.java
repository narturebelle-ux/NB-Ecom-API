package com.narturebelle.dto;

import com.narturebelle.model.PregnancyStage;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Admin create/replace for a ChecklistTask. PUT replaces every field, so send the full record. */
public class ChecklistTaskRequest {

    @NotNull private PregnancyStage stage;
    @NotBlank @Size(max = 255) private String testName;
    @Size(max = 255) private String weeksDue;
    @Size(max = 1500) private String description;
    private Boolean isMandatoryMedicalTest;
    @Size(max = 255) private String category;
    @Size(max = 255) private String recommendedFacilityType;
    @Size(max = 255) private String sponsoredPartnerName;
    @Size(max = 255) private String sponsoredPartnerBadge;
    @Size(max = 255) private String partnerDiscountCode;
    @Size(max = 255) private String partnerBookingUrl;

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
