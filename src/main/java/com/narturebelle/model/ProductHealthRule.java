package com.narturebelle.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * "For mothers with {riskFactor}, {effect} products tagged {tag}" -- e.g. DIABETES, AVOID, "high-sugar".
 * Created PENDING_CLINICAL_REVIEW; reviewed once by a medical reviewer or a verified gynecologist,
 * nutritionist or registered nurse (never its creator), and from then on applied automatically to every
 * mother with that risk factor who has the HEALTH_DATA consent. Who reviewed it, and when, stays on the row.
 */
@Entity
@Table(name = "product_health_rules")
public class ProductHealthRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RiskFactor riskFactor;

    @Column(nullable = false)
    private String tag;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RuleEffect effect;

    /** User-facing; shown in whyRecommended for PREFER rules. Reviewed along with the rule. */
    @Column(nullable = false, length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RuleReviewStatus status;

    @Column(nullable = false)
    private Long createdByUserId;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private Long reviewedByUserId;
    private String reviewedByName;
    /** MEDICAL_REVIEWER, or the reviewing provider's professional type. */
    private String reviewedByRole;
    private LocalDateTime reviewedAt;

    @Column(length = 1000)
    private String rejectionReason;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }

    public ProductHealthRule() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public RiskFactor getRiskFactor() { return riskFactor; }
    public void setRiskFactor(RiskFactor riskFactor) { this.riskFactor = riskFactor; }

    public String getTag() { return tag; }
    public void setTag(String tag) { this.tag = tag; }

    public RuleEffect getEffect() { return effect; }
    public void setEffect(RuleEffect effect) { this.effect = effect; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public RuleReviewStatus getStatus() { return status; }
    public void setStatus(RuleReviewStatus status) { this.status = status; }

    public Long getCreatedByUserId() { return createdByUserId; }
    public void setCreatedByUserId(Long createdByUserId) { this.createdByUserId = createdByUserId; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public Long getReviewedByUserId() { return reviewedByUserId; }
    public void setReviewedByUserId(Long reviewedByUserId) { this.reviewedByUserId = reviewedByUserId; }

    public String getReviewedByName() { return reviewedByName; }
    public void setReviewedByName(String reviewedByName) { this.reviewedByName = reviewedByName; }

    public String getReviewedByRole() { return reviewedByRole; }
    public void setReviewedByRole(String reviewedByRole) { this.reviewedByRole = reviewedByRole; }

    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }

    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
}
