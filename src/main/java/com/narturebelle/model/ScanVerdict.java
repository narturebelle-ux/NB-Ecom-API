package com.narturebelle.model;

/**
 * NOT_SCANNED: Supabase files, which have no scanner (NoOpMalwareScanner; F9-S6).
 * PENDING: an S3 file GuardDuty hasn't tagged yet. It can't be downloaded.
 * CLEAN / INFECTED: GuardDuty's result. Only CLEAN S3 files can be downloaded.
 * SCAN_FAILED: GuardDuty couldn't scan it (unsupported, access denied or failed). It stays blocked for support to check.
 */
public enum ScanVerdict {
    NOT_SCANNED,
    PENDING,
    CLEAN,
    INFECTED,
    SCAN_FAILED
}
