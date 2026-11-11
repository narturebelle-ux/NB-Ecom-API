package com.narturebelle.storage;

import java.time.Instant;

/** What S3 says about an uploaded object: its real size, and its creation time, which S3 lifecycle rules count from. */
public record StoredObjectInfo(long sizeBytes, Instant lastModified) {
}
