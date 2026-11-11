package com.narturebelle.storage;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** D48 (11 Nov 2026): S3 holds all files; Supabase is the database only. */
class StorageDefaultsTest {

    @Test
    void s3WithoutABucket_refusesToStart_withAClearMessage() {
        SupabaseStorageProvider legacy = new SupabaseStorageProvider("", "");
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> new StorageRouter(List.of(legacy), "s3", "health-records"));
        assertTrue(e.getMessage().contains("S3_BUCKET must be set"), e.getMessage());
    }

    @Test
    void legacySupabaseStorage_isOptional_andFailsClearlyOnlyWhenUsed() {
        SupabaseStorageProvider legacy = new SupabaseStorageProvider("", "");
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> legacy.createSignedDownloadUrl("health-records", "records/1/x", Duration.ofMinutes(10)));
        assertTrue(e.getMessage().contains("Files are stored in S3 now"), e.getMessage());
        assertThrows(IllegalStateException.class, () -> legacy.objectExists("health-records", "records/1/x"));
    }
}
