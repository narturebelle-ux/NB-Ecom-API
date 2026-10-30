package com.narturebelle.model;

/** OPEN takes votes; UNDER_REVIEW and IMPLEMENTED stay visible; DECLINED is hidden from the pool. */
public enum IdeaStatus {
    OPEN, UNDER_REVIEW, IMPLEMENTED, DECLINED
}
