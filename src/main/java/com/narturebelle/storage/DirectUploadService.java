package com.narturebelle.storage;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.narturebelle.exception.RecordFileException;
import com.narturebelle.model.FileKind;
import com.narturebelle.model.ScanVerdict;
import com.narturebelle.model.UploadIntent;
import com.narturebelle.model.UploadPurpose;
import com.narturebelle.repository.UploadIntentRepository;
import com.narturebelle.service.RecordFileRules;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Browser-to-S3 uploads for files outside the records vault: provider application documents, provider
 * change-request documents and careers résumés (owner, 12 Nov 2026). Same steps and shapes as /records
 * §10a: upload-rules, upload-url (SINGLE or MULTIPART), part-urls, complete, abort; then the caller's own
 * save call claims the upload. Files go under files/ with random names, so the bucket's existing rules
 * apply (no expiry; GuardDuty scan) and the API's S3 user needs no new permission (S3_SETUP.md).
 *
 * S3 only: when STORAGE_PROVIDER isn't s3 every call answers 404 DIRECT_UPLOAD_UNAVAILABLE, and the UI
 * falls back to the old multipart endpoints, which still work.
 */
@Service
public class DirectUploadService {

    private static final Logger log = LoggerFactory.getLogger(DirectUploadService.class);

    /** Short-lived links only (D48), same as records. */
    private static final Duration UPLOAD_LINK_TTL = Duration.ofMinutes(15);
    private static final Duration DOWNLOAD_LINK_TTL = Duration.ofMinutes(10);
    /** Matches the bucket's AbortIncompleteMultipartUpload lifecycle rule (1 day). */
    private static final Duration MULTIPART_TTL = Duration.ofHours(24);
    /** An upload not saved by a day after its link expired is dropped, file and all. */
    private static final Duration STALE_AFTER = Duration.ofDays(1);
    private static final int MAX_PART_URLS_PER_CALL = 100;

    private final StorageRouter storage;
    private final UploadIntentRepository intents;
    private final ScanPolicy scanPolicy;
    private final RecordFileRules recordRules;
    private final SecureRandom random = new SecureRandom();

    public DirectUploadService(StorageRouter storage, UploadIntentRepository intents, ScanPolicy scanPolicy,
                               RecordFileRules recordRules) {
        this.storage = storage;
        this.intents = intents;
        this.scanPolicy = scanPolicy;
        this.recordRules = recordRules;
    }

    // ---- Types ----

    /** One kind of file a purpose accepts. Extensions are what the UI's file picker offers. */
    public record FileRule(FileKind kind, List<String> mimeTypes, List<String> extensions, long maxBytes) {}

    /**
     * What a purpose accepts. typeCode/sizeCode keep each flow's existing error codes
     * (DOCUMENT_TYPE_NOT_ALLOWED, RESUME_TOO_LARGE, ...). Multipart is used only for videos, and only if allowed.
     */
    public record UploadPolicy(List<FileRule> rules, String typeCode, String sizeCode, boolean allowMultipart, boolean issueToken) {
        FileRule ruleFor(String mimeType) {
            return rules.stream().filter(r -> r.mimeTypes().contains(mimeType)).findFirst().orElse(null);
        }
    }

    /** Who started the upload and for what. ownerUserId is null for careers; contextKey is the question key. */
    public record UploadScope(UploadPurpose purpose, Long ownerUserId, Long contextId, String contextKey) {}

    /** GET .../upload-rules. quotaBytes and usedBytes are always null here: no per-person quota outside records. */
    public record UploadRules(List<FileRule> kinds, Long quotaBytes, Long usedBytes) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record UploadUrl(String mode, String objectPath, String uploadUrl, LocalDateTime expiresAt,
                            Map<String, String> headers, String uploadId, Long partSizeBytes, Integer partCount,
                            String uploadToken) {}

    public record PartLink(int partNumber, String url, LocalDateTime expiresAt) {}

    /** A claimed upload, ready for the caller to save on its own row. */
    public record StoredFile(StorageBackend backend, String bucket, String objectPath, FileKind kind, String mimeType,
                             long sizeBytes, ScanVerdict scanStatus) {}

    // ---- Availability ----

    public boolean available() {
        return storage.activeBackend() == StorageBackend.S3 && storage.s3().isPresent();
    }

    private void requireAvailable() {
        if (!available()) {
            throw new RecordFileException(HttpStatus.NOT_FOUND, "DIRECT_UPLOAD_UNAVAILABLE",
                    "Direct uploads aren't switched on here. Please use the regular upload.");
        }
    }

    // ---- Upload steps ----

    public UploadRules rules(UploadPolicy policy) {
        requireAvailable();
        return new UploadRules(policy.rules(), null, null);
    }

    public UploadUrl issue(UploadPolicy policy, UploadScope scope, String fileName, String mimeType, long sizeBytes) {
        requireAvailable();
        String mime = mimeType == null ? "" : mimeType.trim().toLowerCase(Locale.ROOT);
        FileRule rule = policy.ruleFor(mime);
        if (rule == null || !extensionMatches(fileName, rule)) {
            throw new RecordFileException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, policy.typeCode(),
                    "This file type can't be uploaded here. Allowed: " + String.join(", ", allExtensions(policy)) + ".");
        }
        if (sizeBytes <= 0) {
            throw new RecordFileException(HttpStatus.BAD_REQUEST, "FILE_EMPTY", "That file is empty.");
        }
        if (sizeBytes > rule.maxBytes()) {
            throw new RecordFileException(HttpStatus.PAYLOAD_TOO_LARGE, policy.sizeCode(),
                    "Please upload a file of " + megabytes(rule.maxBytes()) + " MB or less.");
        }

        S3StorageProvider s3 = storage.requireS3();
        String bucket = s3.bucket();
        // Random names with no personal data, under files/ (never videos/: the 30-day rule is for mothers' videos).
        String objectPath = "files/" + UUID.randomUUID();

        UploadIntent intent = new UploadIntent();
        intent.setPurpose(scope.purpose());
        intent.setOwnerUserId(scope.ownerUserId());
        intent.setContextId(scope.contextId());
        intent.setContextKey(scope.contextKey());
        intent.setObjectPath(objectPath);
        intent.setStorageProvider(StorageBackend.S3);
        intent.setKind(rule.kind());
        intent.setMimeType(mime);
        intent.setDeclaredSizeBytes(sizeBytes);
        String token = null;
        if (policy.issueToken()) {
            token = newToken();
            intent.setTokenHash(sha256(token));
        }

        if (policy.allowMultipart() && recordRules.needsMultipart(rule.kind(), sizeBytes)) {
            long partSize = recordRules.partSizeFor(sizeBytes);
            int partCount = (int) ((sizeBytes + partSize - 1) / partSize);
            String uploadId = s3.startMultipartUpload(bucket, objectPath, mime);
            intent.setMultipartUploadId(uploadId);
            intent.setPartCount(partCount);
            intent.setExpiresAt(LocalDateTime.now().plus(MULTIPART_TTL));
            intents.save(intent);
            return new UploadUrl("MULTIPART", objectPath, null, intent.getExpiresAt(), null, uploadId, partSize, partCount, token);
        }

        SignedUploadUrl signed = s3.createSignedUploadUrl(bucket, objectPath, mime, UPLOAD_LINK_TTL);
        intent.setExpiresAt(signed.expiresAt());
        intents.save(intent);
        return new UploadUrl("SINGLE", objectPath, signed.uploadUrl(), signed.expiresAt(),
                Map.of("Content-Type", mime), null, null, null, token);
    }

    /**
     * The part-urls, complete and abort steps only carry the object path, so they match the upload on purpose
     * and owner (scope's context is ignored). The save step (claim) still checks everything.
     */
    public List<PartLink> partUrls(UploadScope scope, String uploadId, String objectPath, List<Integer> partNumbers) {
        requireAvailable();
        UploadIntent intent = requireOpenMultipart(scope, uploadId, objectPath);
        if (partNumbers == null || partNumbers.isEmpty() || partNumbers.size() > MAX_PART_URLS_PER_CALL) {
            throw new RecordFileException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                    "Ask for between 1 and " + MAX_PART_URLS_PER_CALL + " parts at a time.");
        }
        for (Integer n : partNumbers) {
            if (n == null || n < 1 || n > intent.getPartCount()) {
                throw new RecordFileException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                        "Part numbers must be between 1 and " + intent.getPartCount() + ".");
            }
        }
        S3StorageProvider s3 = storage.requireS3();
        return partNumbers.stream().distinct().sorted()
                .map(n -> {
                    SignedUploadUrl link = s3.presignUploadPart(s3.bucket(), objectPath, uploadId, n, UPLOAD_LINK_TTL);
                    return new PartLink(n, link.uploadUrl(), link.expiresAt());
                })
                .toList();
    }

    public void complete(UploadScope scope, String uploadId, String objectPath, List<UploadedPart> parts) {
        requireAvailable();
        UploadIntent intent = requireOpenMultipart(scope, uploadId, objectPath);
        Set<Integer> numbers = new HashSet<>();
        for (UploadedPart p : parts) {
            if (p.etag() == null || p.etag().isBlank()) {
                throw new RecordFileException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Every part needs its ETag.");
            }
            numbers.add(p.partNumber());
        }
        if (numbers.size() != parts.size() || numbers.size() != intent.getPartCount()
                || !numbers.stream().allMatch(n -> n >= 1 && n <= intent.getPartCount())) {
            throw new RecordFileException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                    "Send each of the " + intent.getPartCount() + " parts exactly once.");
        }
        S3StorageProvider s3 = storage.requireS3();
        s3.completeMultipartUpload(s3.bucket(), objectPath, uploadId, parts);
        intent.setCompletedAt(LocalDateTime.now());
        intents.save(intent);
    }

    public void abort(UploadScope scope, String uploadId, String objectPath) {
        requireAvailable();
        UploadIntent intent = intents.findByObjectPathAndPurpose(objectPath == null ? "" : objectPath, scope.purpose())
                .filter(i -> sameOwner(i, scope))
                .filter(i -> uploadId.equals(i.getMultipartUploadId()))
                .orElseThrow(RecordFileException::uploadNotFound);
        S3StorageProvider s3 = storage.requireS3();
        try {
            s3.abortMultipartUpload(s3.bucket(), objectPath, uploadId);
        } catch (Exception alreadyGone) {
            // Already completed, aborted or cleaned up by the lifecycle rule: nothing left to stop.
        }
        intents.delete(intent);
    }

    /**
     * The save step: checks the upload was started by this same person (or token) for this same thing,
     * that it finished, and that what arrived in S3 is the declared size. The intent is removed; the caller
     * stores the returned details on its own row in the same transaction.
     */
    public StoredFile claim(UploadPolicy policy, UploadScope scope, String objectPath, String mimeType, String uploadToken) {
        requireAvailable();
        UploadIntent intent = intents.findByObjectPathAndPurpose(objectPath == null ? "" : objectPath, scope.purpose())
                .filter(i -> matches(i, scope))
                .filter(i -> i.getTokenHash() == null || (uploadToken != null
                        && MessageDigest.isEqual(i.getTokenHash().getBytes(StandardCharsets.UTF_8),
                                sha256(uploadToken).getBytes(StandardCharsets.UTF_8))))
                .orElseThrow(() -> new RecordFileException(HttpStatus.CONFLICT, "UPLOAD_NOT_CONFIRMED",
                        "No upload was started for this file. Please upload it again."));
        if (intent.isMultipart() && intent.getCompletedAt() == null) {
            throw new RecordFileException(HttpStatus.CONFLICT, "UPLOAD_NOT_CONFIRMED", "Finish the upload before saving the file.");
        }
        if (mimeType == null || !intent.getMimeType().equals(mimeType.trim().toLowerCase(Locale.ROOT))) {
            throw new RecordFileException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "The file type doesn't match the upload.");
        }

        S3StorageProvider s3 = storage.requireS3();
        String bucket = s3.bucket();
        FileRule rule = policy.ruleFor(intent.getMimeType());
        long maxBytes = rule == null ? 0 : rule.maxBytes();
        StoredObjectInfo info = s3.describe(bucket, objectPath)
                .orElseThrow(() -> new RecordFileException(HttpStatus.CONFLICT, "UPLOAD_NOT_CONFIRMED",
                        "The file hasn't arrived yet. Please upload it again."));
        if (info.sizeBytes() != intent.getDeclaredSizeBytes() || info.sizeBytes() > maxBytes) {
            // A link only promises a size; what arrived is what counts. Remove anything that doesn't match.
            deleteQuietly(bucket, objectPath);
            intents.delete(intent);
            throw new RecordFileException(HttpStatus.BAD_REQUEST, "FILE_SIZE_MISMATCH",
                    "The uploaded file isn't the size that was declared. Please upload it again.");
        }
        ScanVerdict scan = scanPolicy.required()
                ? readScanQuietly(bucket, objectPath, ScanVerdict.PENDING)
                : scanPolicy.initialS3Verdict();
        intents.delete(intent);
        if (!scanPolicy.required()) {
            log.warn("{} upload saved without a malware scan: scanning is off", scope.purpose());
        }
        return new StoredFile(StorageBackend.S3, bucket, objectPath, intent.getKind(), intent.getMimeType(), info.sizeBytes(), scan);
    }

    // ---- After saving ----

    /** S3 files start PENDING until GuardDuty tags them; returns the latest verdict (unchanged if not final yet). */
    public ScanVerdict refreshScan(StorageBackend backend, String bucket, String objectPath, ScanVerdict current) {
        if (backend != StorageBackend.S3 || current != ScanVerdict.PENDING || storage.s3().isEmpty()) {
            return current;
        }
        return readScanQuietly(bucket, objectPath, ScanVerdict.PENDING);
    }

    public boolean allowsDownload(StorageBackend backend, ScanVerdict verdict) {
        return scanPolicy.allowsDownload(backend, verdict);
    }

    /**
     * A 10-minute link to an S3 file. 409 FILE_BLOCKED / FILE_NOT_READY as in records. "attachment" saves
     * it under attachmentName (no personal data: it appears in S3 logs); anything else opens inline.
     */
    public SignedDownloadUrl downloadUrl(String bucket, String objectPath, ScanVerdict verdict, String mimeType,
                                         String disposition, String attachmentName) {
        if (verdict == ScanVerdict.INFECTED || verdict == ScanVerdict.SCAN_FAILED) {
            throw new RecordFileException(HttpStatus.CONFLICT, "FILE_BLOCKED",
                    "This file can't be opened because it didn't pass the safety check.");
        }
        if (!scanPolicy.allowsDownload(StorageBackend.S3, verdict)) {
            throw RecordFileException.notReady();
        }
        String contentDisposition = "attachment".equalsIgnoreCase(disposition)
                ? "attachment; filename=\"" + attachmentName + "\""
                : "inline";
        return storage.requireS3().createSignedDownloadUrl(bucket, objectPath, DOWNLOAD_LINK_TTL, mimeType, contentDisposition);
    }

    public String extensionFor(String mimeType) {
        return recordRules.extensionFor(mimeType);
    }

    /** Best effort: the row is what matters; the bucket's lifecycle rule clears anything left behind. */
    public void deleteQuietly(String bucket, String objectPath) {
        try {
            storage.requireS3().deleteObject(bucket, objectPath);
        } catch (Exception bestEffort) {
            log.warn("Couldn't delete an S3 object: {}", bestEffort.getClass().getSimpleName());
        }
    }

    /** Drops uploads never saved (for example a careers form abandoned after the upload) and their files. */
    public int sweepStale(LocalDateTime now) {
        if (storage.s3().isEmpty()) return 0;
        List<UploadIntent> stale = intents.findTop200ByExpiresAtBefore(now.minus(STALE_AFTER));
        S3StorageProvider s3 = storage.requireS3();
        for (UploadIntent intent : stale) {
            if (intent.isMultipart()) {
                try { s3.abortMultipartUpload(s3.bucket(), intent.getObjectPath(), intent.getMultipartUploadId()); }
                catch (Exception alreadyGone) { /* completed or cleaned up by the lifecycle rule */ }
            }
            deleteQuietly(s3.bucket(), intent.getObjectPath());
            intents.delete(intent);
        }
        return stale.size();
    }

    // ---- Helpers ----

    private UploadIntent requireOpenMultipart(UploadScope scope, String uploadId, String objectPath) {
        return intents.findByObjectPathAndPurpose(objectPath == null ? "" : objectPath, scope.purpose())
                .filter(i -> sameOwner(i, scope))
                .filter(i -> uploadId.equals(i.getMultipartUploadId()))
                .filter(i -> i.getCompletedAt() == null)
                .filter(i -> i.getExpiresAt().isAfter(LocalDateTime.now()))
                .orElseThrow(RecordFileException::uploadNotFound);
    }

    private static boolean sameOwner(UploadIntent intent, UploadScope scope) {
        return intent.getPurpose() == scope.purpose() && intent.getOwnerUserId() != null
                && intent.getOwnerUserId().equals(scope.ownerUserId());
    }

    private static boolean matches(UploadIntent intent, UploadScope scope) {
        return intent.getPurpose() == scope.purpose()
                && Objects.equals(intent.getOwnerUserId(), scope.ownerUserId())
                && Objects.equals(intent.getContextId(), scope.contextId())
                && Objects.equals(intent.getContextKey(), scope.contextKey());
    }

    private ScanVerdict readScanQuietly(String bucket, String objectPath, ScanVerdict fallback) {
        try {
            return storage.requireS3().readScanStatus(bucket, objectPath);
        } catch (Exception e) {
            log.warn("Couldn't read the malware scan tag yet: {}", e.getClass().getSimpleName());
            return fallback;
        }
    }

    private static boolean extensionMatches(String fileName, FileRule rule) {
        if (fileName == null) return false;
        int dot = fileName.lastIndexOf('.');
        String extension = dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        return rule.extensions().contains(extension);
    }

    private static List<String> allExtensions(UploadPolicy policy) {
        return policy.rules().stream().flatMap(r -> r.extensions().stream()).map(e -> e.toUpperCase(Locale.ROOT)).distinct().toList();
    }

    private static long megabytes(long bytes) {
        return bytes / (1024 * 1024);
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
