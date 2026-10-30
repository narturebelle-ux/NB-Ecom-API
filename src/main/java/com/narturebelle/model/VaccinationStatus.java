package com.narturebelle.model;

/** Computed per request from dueDate vs. today -- never stored, so it can't go stale. */
public enum VaccinationStatus {
    DONE,
    OVERDUE,
    DUE,
    UPCOMING
}
