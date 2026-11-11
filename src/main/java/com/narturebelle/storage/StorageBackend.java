package com.narturebelle.storage;

/** Where a file's bytes live. Stored on each record so old Supabase files keep working while new ones go to S3 (D48). */
public enum StorageBackend {
    SUPABASE,
    S3
}
