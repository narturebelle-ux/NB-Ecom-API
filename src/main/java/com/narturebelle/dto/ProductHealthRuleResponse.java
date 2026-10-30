package com.narturebelle.dto;

import com.narturebelle.model.ProductHealthRule;
import com.narturebelle.model.RiskFactor;
import com.narturebelle.model.RuleEffect;
import com.narturebelle.model.RuleReviewStatus;

import java.time.LocalDateTime;

/** Full rule including its review trail -- admin and reviewer views only; never sent to mothers. */
public class ProductHealthRuleResponse {

    private Long id;
    private RiskFactor riskFactor;
    private String tag;
    private RuleEffect effect;
    private String reason;
    private RuleReviewStatus status;
    private Long createdByUserId;
    private LocalDateTime createdAt;
    private Long reviewedByUserId;
    private String reviewedByName;
    private String reviewedByRole;
    private LocalDateTime reviewedAt;
    private String rejectionReason;

    public static ProductHealthRuleResponse from(ProductHealthRule rule) {
        ProductHealthRuleResponse dto = new ProductHealthRuleResponse();
        dto.id = rule.getId();
        dto.riskFactor = rule.getRiskFactor();
        dto.tag = rule.getTag();
        dto.effect = rule.getEffect();
        dto.reason = rule.getReason();
        dto.status = rule.getStatus();
        dto.createdByUserId = rule.getCreatedByUserId();
        dto.createdAt = rule.getCreatedAt();
        dto.reviewedByUserId = rule.getReviewedByUserId();
        dto.reviewedByName = rule.getReviewedByName();
        dto.reviewedByRole = rule.getReviewedByRole();
        dto.reviewedAt = rule.getReviewedAt();
        dto.rejectionReason = rule.getRejectionReason();
        return dto;
    }

    public Long getId() { return id; }
    public RiskFactor getRiskFactor() { return riskFactor; }
    public String getTag() { return tag; }
    public RuleEffect getEffect() { return effect; }
    public String getReason() { return reason; }
    public RuleReviewStatus getStatus() { return status; }
    public Long getCreatedByUserId() { return createdByUserId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public Long getReviewedByUserId() { return reviewedByUserId; }
    public String getReviewedByName() { return reviewedByName; }
    public String getReviewedByRole() { return reviewedByRole; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public String getRejectionReason() { return rejectionReason; }
}
