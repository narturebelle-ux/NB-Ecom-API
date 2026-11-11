package com.narturebelle.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Picks where files go (D48). STORAGE_PROVIDER chooses the backend for new uploads: "s3" (default; all
 * files and videos) or "supabase" (legacy, for old setups and tests). Existing files are always read from
 * the backend stored on their record.
 */
@Component
public class StorageRouter {

    private final StorageProvider supabase;
    private final S3StorageProvider s3;
    private final String supabaseBucket;
    private final StorageBackend active;

    public StorageRouter(List<StorageProvider> providers,
                         @Value("${storage.provider:supabase}") String activeSetting,
                         @Value("${supabase.storage.bucket}") String supabaseBucket) {
        this.s3 = providers.stream()
                .filter(S3StorageProvider.class::isInstance).map(S3StorageProvider.class::cast)
                .findFirst().orElse(null);
        this.supabase = providers.stream()
                .filter(p -> !(p instanceof S3StorageProvider))
                .findFirst().orElseThrow(() -> new IllegalStateException("Supabase storage is missing."));
        this.supabaseBucket = supabaseBucket;
        this.active = switch (activeSetting.trim().toLowerCase()) {
            case "supabase" -> StorageBackend.SUPABASE;
            case "s3" -> StorageBackend.S3;
            default -> throw new IllegalStateException("STORAGE_PROVIDER must be 'supabase' or 's3'.");
        };
        if (active == StorageBackend.S3 && s3 == null) {
            throw new IllegalStateException("Files are stored in S3 (the default, D48), so S3_BUCKET must be set "
                    + "(with S3_REGION and the NB_S3_* keys). See S3_SETUP.md.");
        }
    }

    public StorageBackend activeBackend() {
        return active;
    }

    /** Videos need multipart uploads and lifecycle rules, so they are only accepted on S3 (D49). */
    public boolean videosEnabled() {
        return active == StorageBackend.S3;
    }

    public StorageProvider provider(StorageBackend backend) {
        return backend == StorageBackend.S3 ? requireS3() : supabase;
    }

    public String bucket(StorageBackend backend) {
        return backend == StorageBackend.S3 ? requireS3().bucket() : supabaseBucket;
    }

    public Optional<S3StorageProvider> s3() {
        return Optional.ofNullable(s3);
    }

    public S3StorageProvider requireS3() {
        if (s3 == null) {
            throw new IllegalStateException("This file is stored on S3, but S3_BUCKET is not set.");
        }
        return s3;
    }
}
