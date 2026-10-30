package com.narturebelle.model;

/** REQUESTED -> IN_PROGRESS -> COMPLETED, or REJECTED by an admin; CANCELLED by the user while still REQUESTED. */
public enum DeletionRequestStatus {
    REQUESTED,
    IN_PROGRESS,
    COMPLETED,
    REJECTED,
    CANCELLED
}
