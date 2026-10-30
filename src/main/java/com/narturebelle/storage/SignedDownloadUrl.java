package com.narturebelle.storage;

import java.time.LocalDateTime;

public record SignedDownloadUrl(String downloadUrl, LocalDateTime expiresAt) {
}
