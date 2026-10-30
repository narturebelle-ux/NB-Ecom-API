package com.narturebelle.storage;

import java.time.Duration;

/** The seam a later storage/region swap plugs into — mirrors com.narturebelle.payment.PaymentProvider's role for Phase 7. */
public interface StorageProvider {

    SignedUploadUrl createSignedUploadUrl(String bucket, String objectPath, Duration expiry);

    SignedDownloadUrl createSignedDownloadUrl(String bucket, String objectPath, Duration expiry);

    boolean objectExists(String bucket, String objectPath);

    void deleteObject(String bucket, String objectPath);
}
