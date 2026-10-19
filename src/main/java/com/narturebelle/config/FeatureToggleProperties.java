package com.narturebelle.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "features")
public class FeatureToggleProperties {

    private boolean nurseCompanion = true;
    private boolean hospitalLeads = true;
    private boolean doctorLiveSessions = true;
    private boolean assessmentVoucher = true;
    private boolean babyCareProducts = false;

    public FeatureToggleProperties() {}

    public boolean isNurseCompanion() { return nurseCompanion; }
    public void setNurseCompanion(boolean nurseCompanion) { this.nurseCompanion = nurseCompanion; }

    public boolean isHospitalLeads() { return hospitalLeads; }
    public void setHospitalLeads(boolean hospitalLeads) { this.hospitalLeads = hospitalLeads; }

    public boolean isDoctorLiveSessions() { return doctorLiveSessions; }
    public void setDoctorLiveSessions(boolean doctorLiveSessions) { this.doctorLiveSessions = doctorLiveSessions; }

    public boolean isAssessmentVoucher() { return assessmentVoucher; }
    public void setAssessmentVoucher(boolean assessmentVoucher) { this.assessmentVoucher = assessmentVoucher; }

    public boolean isBabyCareProducts() { return babyCareProducts; }
    public void setBabyCareProducts(boolean babyCareProducts) { this.babyCareProducts = babyCareProducts; }

    public Map<String, Boolean> toMap() {
        Map<String, Boolean> map = new HashMap<>();
        map.put("nurseCompanion", nurseCompanion);
        map.put("hospitalLeads", hospitalLeads);
        map.put("doctorLiveSessions", doctorLiveSessions);
        map.put("assessmentVoucher", assessmentVoucher);
        map.put("babyCareProducts", babyCareProducts);
        return map;
    }
}
