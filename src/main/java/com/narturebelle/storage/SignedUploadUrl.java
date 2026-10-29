package com.narturebelle.storage;

import java.time.LocalDateTime;

public record SignedUploadUrl(String uploadUrl, String token, LocalDateTime expiresAt) {
}
