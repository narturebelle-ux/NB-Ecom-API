package com.narturebelle.dto;

import com.narturebelle.model.HealthProfile;
import com.narturebelle.model.RiskFactor;

import java.time.LocalDateTime;
import java.util.Set;

public class HealthProfileResponse {

    private Boolean firstPregnancy;
    private Boolean multiples;
    private Set<RiskFactor> riskFactors;
    private LocalDateTime updatedAt;

    public static HealthProfileResponse from(HealthProfile profile) {
        HealthProfileResponse dto = new HealthProfileResponse();
        dto.firstPregnancy = profile.getFirstPregnancy();
        dto.multiples = profile.getMultiples();
        dto.riskFactors = Set.copyOf(profile.getRiskFactors());
        dto.updatedAt = profile.getUpdatedAt();
        return dto;
    }

    public Boolean getFirstPregnancy() { return firstPregnancy; }
    public Boolean getMultiples() { return multiples; }
    public Set<RiskFactor> getRiskFactors() { return riskFactors; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
