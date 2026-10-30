package com.narturebelle.dto;

import com.narturebelle.model.RiskFactor;

import java.util.Set;

/** PUT /me/health-profile replaces the whole profile: omitted fields are cleared. */
public class HealthProfileRequest {

    private Boolean firstPregnancy;
    private Boolean multiples;
    private Set<RiskFactor> riskFactors;

    public Boolean getFirstPregnancy() { return firstPregnancy; }
    public void setFirstPregnancy(Boolean firstPregnancy) { this.firstPregnancy = firstPregnancy; }

    public Boolean getMultiples() { return multiples; }
    public void setMultiples(Boolean multiples) { this.multiples = multiples; }

    public Set<RiskFactor> getRiskFactors() { return riskFactors; }
    public void setRiskFactors(Set<RiskFactor> riskFactors) { this.riskFactors = riskFactors; }
}
