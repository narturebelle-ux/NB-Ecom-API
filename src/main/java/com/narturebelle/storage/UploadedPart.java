package com.narturebelle.storage;

/** One finished part of a multipart upload: the browser reads the ETag from S3's response to its PUT. */
public record UploadedPart(int partNumber, String etag) {
}
