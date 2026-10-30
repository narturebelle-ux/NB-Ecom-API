package com.narturebelle.model;

/** Only APPROVED rules ever reach a recommendation. */
public enum RuleReviewStatus {
    PENDING_CLINICAL_REVIEW,
    APPROVED,
    REJECTED
}
