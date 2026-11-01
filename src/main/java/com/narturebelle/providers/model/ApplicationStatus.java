package com.narturebelle.providers.model;

/**
 * Where an application is. The approved document's "Documents pending" is DRAFT or CHANGES_REQUESTED, and
 * its "Under review" is OPERATIONS_REVIEW or MEDICAL_REVIEW.
 */
public enum ApplicationStatus {
    DRAFT,
    CHANGES_REQUESTED,
    OPERATIONS_REVIEW,
    MEDICAL_REVIEW,
    VERIFIED,
    ACTIVE,
    PAUSED,
    SUSPENDED,
    REJECTED,
    WITHDRAWN,
    CLOSED;

    /** Finished without going live. A new application can follow, subject to the reapply rules. */
    public boolean isClosed() {
        return this == REJECTED || this == WITHDRAWN || this == CLOSED;
    }

    /** The applicant can still edit answers and documents. */
    public boolean isEditable() {
        return this == DRAFT || this == CHANGES_REQUESTED;
    }

    /** The Medical Reviewer has approved, so a public.providers row exists. */
    public boolean isApproved() {
        return this == VERIFIED || this == ACTIVE || this == PAUSED || this == SUSPENDED;
    }
}
