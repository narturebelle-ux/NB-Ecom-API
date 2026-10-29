package com.narturebelle.model;

/** NOT_SCANNED is the only value any code in this system actually produces — no real malware scanner is integrated yet. */
public enum ScanVerdict {
    NOT_SCANNED,
    CLEAN,
    INFECTED
}
