package com.narturebelle.dto;

import com.narturebelle.model.RiskFactor;
import com.narturebelle.model.RuleEffect;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class CreateProductHealthRuleRequest {

    @NotNull private RiskFactor riskFactor;
    @NotBlank @Size(max = 50) private String tag;
    @NotNull private RuleEffect effect;
    /** User-facing wording, e.g. "Sugar-free, which suits a diabetes-friendly diet". Reviewed with the rule. */
    @NotBlank @Size(max = 500) private String reason;

    public RiskFactor getRiskFactor() { return riskFactor; }
    public void setRiskFactor(RiskFactor riskFactor) { this.riskFactor = riskFactor; }
    public String getTag() { return tag; }
    public void setTag(String tag) { this.tag = tag; }
    public RuleEffect getEffect() { return effect; }
    public void setEffect(RuleEffect effect) { this.effect = effect; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
