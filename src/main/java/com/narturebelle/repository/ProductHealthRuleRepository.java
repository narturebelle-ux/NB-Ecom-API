package com.narturebelle.repository;

import com.narturebelle.model.ProductHealthRule;
import com.narturebelle.model.RiskFactor;
import com.narturebelle.model.RuleReviewStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ProductHealthRuleRepository extends JpaRepository<ProductHealthRule, Long> {
    List<ProductHealthRule> findAllByOrderByCreatedAtDesc();
    List<ProductHealthRule> findByStatusOrderByCreatedAtAsc(RuleReviewStatus status);
    List<ProductHealthRule> findByStatusAndRiskFactorIn(RuleReviewStatus status, Collection<RiskFactor> riskFactors);
}
