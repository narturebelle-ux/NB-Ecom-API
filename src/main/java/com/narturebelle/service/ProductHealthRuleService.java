package com.narturebelle.service;

import com.narturebelle.dto.CreateProductHealthRuleRequest;
import com.narturebelle.exception.ProductHealthRuleException;
import com.narturebelle.model.ProductHealthRule;
import com.narturebelle.model.RiskFactor;
import com.narturebelle.model.RuleReviewStatus;
import com.narturebelle.repository.ProductHealthRuleRepository;
import com.narturebelle.security.AuditService;
import com.narturebelle.security.AuthorizationService.RuleReviewer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Clinically reviewed product health rules (see ProductHealthRule). Reviewed once, then applied automatically. */
@Service
public class ProductHealthRuleService {

    private final ProductHealthRuleRepository ruleRepository;
    private final AuditService auditService;

    public ProductHealthRuleService(ProductHealthRuleRepository ruleRepository, AuditService auditService) {
        this.ruleRepository = ruleRepository;
        this.auditService = auditService;
    }

    public ProductHealthRule create(Long creatorUserId, CreateProductHealthRuleRequest req) {
        ProductHealthRule rule = new ProductHealthRule();
        rule.setRiskFactor(req.getRiskFactor());
        rule.setTag(req.getTag().trim().toLowerCase(Locale.ROOT));
        rule.setEffect(req.getEffect());
        rule.setReason(req.getReason().trim());
        rule.setStatus(RuleReviewStatus.PENDING_CLINICAL_REVIEW);
        rule.setCreatedByUserId(creatorUserId);
        ProductHealthRule saved = ruleRepository.save(rule);
        auditService.record("PRODUCT_HEALTH_RULE_CREATED", "PRODUCT_HEALTH_RULE", String.valueOf(saved.getId()),
                "Admin proposed a product health rule");
        return saved;
    }

    public List<ProductHealthRule> list(RuleReviewStatus status) {
        return status == null ? ruleRepository.findAllByOrderByCreatedAtDesc() : ruleRepository.findByStatusOrderByCreatedAtAsc(status);
    }

    /** Deleting an approved rule stops it applying from the next request on. */
    public void delete(Long id) {
        ProductHealthRule rule = require(id);
        ruleRepository.delete(rule);
        auditService.record("PRODUCT_HEALTH_RULE_DELETED", "PRODUCT_HEALTH_RULE", String.valueOf(id),
                "Admin deleted a product health rule", Map.of("status", rule.getStatus()));
    }

    @Transactional
    public ProductHealthRule approve(Long id, RuleReviewer reviewer) {
        ProductHealthRule rule = requireReviewable(id, reviewer);
        rule.setStatus(RuleReviewStatus.APPROVED);
        return review(rule, reviewer, null, "PRODUCT_HEALTH_RULE_APPROVED");
    }

    @Transactional
    public ProductHealthRule reject(Long id, RuleReviewer reviewer, String reason) {
        ProductHealthRule rule = requireReviewable(id, reviewer);
        rule.setStatus(RuleReviewStatus.REJECTED);
        return review(rule, reviewer, reason.trim(), "PRODUCT_HEALTH_RULE_REJECTED");
    }

    /** Only APPROVED rules for the given risk factors -- what recommendations apply. */
    public List<ProductHealthRule> approvedFor(Collection<RiskFactor> riskFactors) {
        if (riskFactors.isEmpty()) return List.of();
        return ruleRepository.findByStatusAndRiskFactorIn(RuleReviewStatus.APPROVED, riskFactors);
    }

    private ProductHealthRule review(ProductHealthRule rule, RuleReviewer reviewer, String rejectionReason, String action) {
        rule.setReviewedByUserId(reviewer.userId());
        rule.setReviewedByName(reviewer.name());
        rule.setReviewedByRole(reviewer.role());
        rule.setReviewedAt(LocalDateTime.now().truncatedTo(ChronoUnit.MICROS));
        rule.setRejectionReason(rejectionReason);
        ProductHealthRule saved = ruleRepository.save(rule);
        auditService.record(action, "PRODUCT_HEALTH_RULE", String.valueOf(saved.getId()),
                "Product health rule reviewed", Map.of("reviewerRole", reviewer.role()));
        return saved;
    }

    private ProductHealthRule requireReviewable(Long id, RuleReviewer reviewer) {
        ProductHealthRule rule = require(id);
        if (rule.getStatus() != RuleReviewStatus.PENDING_CLINICAL_REVIEW) {
            throw new ProductHealthRuleException(409, "RULE_ALREADY_REVIEWED", "This rule has already been " + rule.getStatus() + ".");
        }
        if (rule.getCreatedByUserId().equals(reviewer.userId())) {
            throw new ProductHealthRuleException(409, "RULE_SELF_REVIEW_NOT_ALLOWED", "You can't review a rule you created.");
        }
        return rule;
    }

    private ProductHealthRule require(Long id) {
        return ruleRepository.findById(id).orElseThrow(() ->
                new ProductHealthRuleException(404, "PRODUCT_HEALTH_RULE_NOT_FOUND", "Product health rule " + id + " not found."));
    }
}
