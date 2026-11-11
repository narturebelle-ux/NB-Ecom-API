package com.narturebelle.service;

import com.narturebelle.dto.CreateRecordShareRequest;
import com.narturebelle.dto.CreateRecordUploadUrlRequest;
import com.narturebelle.dto.RegisterRecordRequest;
import com.narturebelle.exception.InvalidRecordRequestException;
import com.narturebelle.exception.RecordFileException;
import com.narturebelle.exception.RecordNotAllowedException;
import com.narturebelle.exception.RecordUploadNotConfirmedException;
import com.narturebelle.model.Booking;
import com.narturebelle.model.FileKind;
import com.narturebelle.model.HealthRecord;
import com.narturebelle.model.PendingUpload;
import com.narturebelle.model.RecordAccessAction;
import com.narturebelle.model.RecordAccessEvent;
import com.narturebelle.model.RecordAccessRelationship;
import com.narturebelle.model.RecordShare;
import com.narturebelle.model.RecordStatus;
import com.narturebelle.model.ScanVerdict;
import com.narturebelle.model.ShareTargetType;
import com.narturebelle.repository.BookingRepository;
import com.narturebelle.repository.HealthRecordRepository;
import com.narturebelle.repository.PendingUploadRepository;
import com.narturebelle.repository.RecordAccessEventRepository;
import com.narturebelle.repository.RecordShareRepository;
import com.narturebelle.security.AuditService;
import com.narturebelle.storage.MalwareScanner;
import com.narturebelle.storage.S3StorageProvider;
import com.narturebelle.storage.ScanPolicy;
import com.narturebelle.storage.SignedDownloadUrl;
import com.narturebelle.storage.SignedUploadUrl;
import com.narturebelle.storage.StorageBackend;
import com.narturebelle.storage.StorageRouter;
import com.narturebelle.storage.StoredObjectInfo;
import com.narturebelle.storage.UploadedPart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Component
public class RecordService {

    private static final Logger log = LoggerFactory.getLogger(RecordService.class);

    /** Short-lived links only (D48): 15 minutes to upload, 10 to download. */
    private static final Duration UPLOAD_LINK_TTL = Duration.ofMinutes(15);
    private static final Duration DOWNLOAD_LINK_TTL = Duration.ofMinutes(10);
    /** Matches the bucket's AbortIncompleteMultipartUpload lifecycle rule (1 day). */
    private static final Duration MULTIPART_TTL = Duration.ofHours(24);
    private static final int MAX_PART_URLS_PER_CALL = 100;

    private final HealthRecordRepository healthRecordRepository;
    private final RecordShareRepository recordShareRepository;
    private final RecordAccessEventRepository recordAccessEventRepository;
    private final BookingRepository bookingRepository;
    private final PendingUploadRepository pendingUploadRepository;
    private final StorageRouter storage;
    private final MalwareScanner malwareScanner;
    private final AuditService auditService;
    private final RecordFileRules rules;
    private final ScanPolicy scanPolicy;
    private final long maxShareExpiryDays;

    public RecordService(HealthRecordRepository healthRecordRepository, RecordShareRepository recordShareRepository,
                          RecordAccessEventRepository recordAccessEventRepository, BookingRepository bookingRepository,
                          PendingUploadRepository pendingUploadRepository, StorageRouter storage,
                          MalwareScanner malwareScanner, AuditService auditService, RecordFileRules rules,
                          ScanPolicy scanPolicy,
                          @Value("${records.share.max-expiry-days}") long maxShareExpiryDays) {
        this.scanPolicy = scanPolicy;
        this.healthRecordRepository = healthRecordRepository;
        this.recordShareRepository = recordShareRepository;
        this.recordAccessEventRepository = recordAccessEventRepository;
        this.bookingRepository = bookingRepository;
        this.pendingUploadRepository = pendingUploadRepository;
        this.storage = storage;
        this.malwareScanner = malwareScanner;
        this.auditService = auditService;
        this.rules = rules;
        this.maxShareExpiryDays = maxShareExpiryDays;
    }

    // ---- Upload rules ----

    public UploadRules uploadRules(Long ownerUserId) {
        List<KindRule> kinds = new java.util.ArrayList<>();
        kinds.add(kindRule(FileKind.DOCUMENT));
        if (storage.videosEnabled()) kinds.add(kindRule(FileKind.VIDEO));
        return new UploadRules(kinds, rules.quotaBytes(),
                healthRecordRepository.sumActiveBytes(ownerUserId, LocalDateTime.now()));
    }

    private KindRule kindRule(FileKind kind) {
        return new KindRule(kind, List.copyOf(rules.mimeTypes(kind)), rules.extensions(kind), rules.maxBytes(kind));
    }

    // ---- Upload ----

    public RecordUploadIntent issueUploadUrl(Long ownerUserId, CreateRecordUploadUrlRequest req) {
        FileKind kind = rules.kindFor(req.getMimeType());
        if (kind == FileKind.VIDEO && !storage.videosEnabled()) {
            throw new InvalidRecordRequestException("mimeType", "Videos can't be uploaded yet.");
        }
        long maxBytes = rules.maxBytes(kind);
        if (req.getSizeBytes() > maxBytes) {
            throw new InvalidRecordRequestException("sizeBytes", "File exceeds the maximum allowed size of " + maxBytes + " bytes.");
        }
        long used = healthRecordRepository.sumActiveBytes(ownerUserId, LocalDateTime.now());
        if (used + req.getSizeBytes() > rules.quotaBytes()) {
            throw new RecordFileException(HttpStatus.CONFLICT, "STORAGE_QUOTA_EXCEEDED",
                    "There isn't enough space left for this file. Delete some files and try again.");
        }

        StorageBackend backend = storage.activeBackend();
        String bucket = storage.bucket(backend);
        // S3 keys are fully random with no personal data; Supabase keeps its existing layout.
        String objectPath = backend == StorageBackend.S3
                ? (kind == FileKind.VIDEO ? "videos/" : "files/") + UUID.randomUUID()
                : "records/" + ownerUserId + "/" + UUID.randomUUID();

        PendingUpload pending = new PendingUpload();
        pending.setUserId(ownerUserId);
        pending.setObjectPath(objectPath);
        pending.setStorageProvider(backend);
        pending.setKind(kind);
        pending.setMimeType(req.getMimeType());
        pending.setDeclaredSizeBytes(req.getSizeBytes());

        if (backend == StorageBackend.S3 && rules.needsMultipart(kind, req.getSizeBytes())) {
            S3StorageProvider s3 = storage.requireS3();
            long partSize = rules.partSizeFor(req.getSizeBytes());
            int partCount = (int) ((req.getSizeBytes() + partSize - 1) / partSize);
            String uploadId = s3.startMultipartUpload(bucket, objectPath, req.getMimeType());
            pending.setMultipartUploadId(uploadId);
            pending.setPartCount(partCount);
            pending.setExpiresAt(LocalDateTime.now().plus(MULTIPART_TTL));
            pendingUploadRepository.save(pending);
            return RecordUploadIntent.multipart(objectPath, uploadId, partSize, partCount, pending.getExpiresAt());
        }

        SignedUploadUrl signed;
        Map<String, String> headers;
        if (backend == StorageBackend.S3) {
            signed = storage.requireS3().createSignedUploadUrl(bucket, objectPath, req.getMimeType(), UPLOAD_LINK_TTL);
            headers = Map.of("Content-Type", req.getMimeType());
        } else {
            signed = storage.provider(backend).createSignedUploadUrl(bucket, objectPath, UPLOAD_LINK_TTL);
            headers = Map.of();
        }
        pending.setExpiresAt(signed.expiresAt());
        pendingUploadRepository.save(pending);
        return RecordUploadIntent.single(objectPath, signed.uploadUrl(), signed.token(), signed.expiresAt(), headers);
    }

    public List<PartLink> issuePartUrls(Long ownerUserId, String uploadId, String objectPath, List<Integer> partNumbers) {
        PendingUpload pending = requireOpenMultipart(ownerUserId, uploadId, objectPath);
        if (partNumbers == null || partNumbers.isEmpty() || partNumbers.size() > MAX_PART_URLS_PER_CALL) {
            throw new InvalidRecordRequestException("partNumbers", "Ask for between 1 and " + MAX_PART_URLS_PER_CALL + " parts at a time.");
        }
        for (Integer n : partNumbers) {
            if (n == null || n < 1 || n > pending.getPartCount()) {
                throw new InvalidRecordRequestException("partNumbers", "Part numbers must be between 1 and " + pending.getPartCount() + ".");
            }
        }
        S3StorageProvider s3 = storage.requireS3();
        String bucket = s3.bucket();
        return partNumbers.stream().distinct().sorted()
                .map(n -> {
                    SignedUploadUrl link = s3.presignUploadPart(bucket, objectPath, uploadId, n, UPLOAD_LINK_TTL);
                    return new PartLink(n, link.uploadUrl(), link.expiresAt());
                })
                .toList();
    }

    public void completeMultipart(Long ownerUserId, String uploadId, String objectPath, List<UploadedPart> parts) {
        PendingUpload pending = requireOpenMultipart(ownerUserId, uploadId, objectPath);
        Set<Integer> numbers = new java.util.HashSet<>();
        for (UploadedPart p : parts) {
            if (p.etag() == null || p.etag().isBlank()) {
                throw new InvalidRecordRequestException("parts", "Every part needs its ETag.");
            }
            numbers.add(p.partNumber());
        }
        if (numbers.size() != parts.size() || numbers.size() != pending.getPartCount()
                || !numbers.stream().allMatch(n -> n >= 1 && n <= pending.getPartCount())) {
            throw new InvalidRecordRequestException("parts", "Send each of the " + pending.getPartCount() + " parts exactly once.");
        }
        S3StorageProvider s3 = storage.requireS3();
        s3.completeMultipartUpload(s3.bucket(), objectPath, uploadId, parts);
        pending.setCompletedAt(LocalDateTime.now());
        pendingUploadRepository.save(pending);
    }

    public void abortMultipart(Long ownerUserId, String uploadId, String objectPath) {
        PendingUpload pending = pendingUploadRepository.findByObjectPathAndUserId(objectPath, ownerUserId)
                .filter(p -> uploadId.equals(p.getMultipartUploadId()))
                .orElseThrow(RecordFileException::uploadNotFound);
        S3StorageProvider s3 = storage.requireS3();
        try {
            s3.abortMultipartUpload(s3.bucket(), objectPath, uploadId);
        } catch (Exception alreadyGone) {
            // Already completed, aborted or cleaned up by the lifecycle rule: nothing left to stop.
        }
        pendingUploadRepository.delete(pending);
    }

    private PendingUpload requireOpenMultipart(Long ownerUserId, String uploadId, String objectPath) {
        return pendingUploadRepository.findByObjectPathAndUserId(objectPath, ownerUserId)
                .filter(p -> uploadId.equals(p.getMultipartUploadId()))
                .filter(p -> p.getCompletedAt() == null)
                .filter(p -> p.getExpiresAt().isAfter(LocalDateTime.now()))
                .orElseThrow(RecordFileException::uploadNotFound);
    }

    public HealthRecord registerUpload(Long ownerUserId, RegisterRecordRequest req) {
        // The path must be one this API handed to this same mother: nobody can claim someone else's upload.
        PendingUpload pending = pendingUploadRepository.findByObjectPathAndUserId(req.getObjectPath(), ownerUserId)
                .orElseThrow(() -> new RecordUploadNotConfirmedException(
                        "No upload was started for this file — request an upload link first."));
        if (pending.isMultipart() && pending.getCompletedAt() == null) {
            throw new RecordUploadNotConfirmedException("Finish the upload before saving the file.");
        }
        if (!pending.getMimeType().equals(req.getMimeType())) {
            throw new InvalidRecordRequestException("mimeType", "The file type doesn't match the upload.");
        }

        StorageBackend backend = pending.getStorageProvider();
        String bucket = storage.bucket(backend);
        long sizeBytes = req.getSizeBytes();
        Instant created = Instant.now();
        ScanVerdict scan;

        if (backend == StorageBackend.S3) {
            S3StorageProvider s3 = storage.requireS3();
            StoredObjectInfo info = s3.describe(bucket, req.getObjectPath())
                    .orElseThrow(() -> new RecordUploadNotConfirmedException(
                            "No uploaded object found at the given path — upload to the signed URL before registering."));
            if (info.sizeBytes() != pending.getDeclaredSizeBytes() || info.sizeBytes() > rules.maxBytes(pending.getKind())) {
                // A link only promises a size; what arrived is what counts. Remove anything that doesn't match.
                try { s3.deleteObject(bucket, req.getObjectPath()); } catch (Exception bestEffort) { /* lifecycle cleans up */ }
                pendingUploadRepository.delete(pending);
                throw new InvalidRecordRequestException("sizeBytes", "The uploaded file isn't the size that was declared. Please upload it again.");
            }
            sizeBytes = info.sizeBytes();
            if (info.lastModified() != null) created = info.lastModified();
            scan = scanPolicy.required()
                    ? readScanQuietly(s3, bucket, req.getObjectPath(), ScanVerdict.PENDING)
                    : scanPolicy.initialS3Verdict();
        } else {
            if (!storage.provider(backend).objectExists(bucket, req.getObjectPath())) {
                throw new RecordUploadNotConfirmedException(
                        "No uploaded object found at the given path — upload to the signed URL before registering.");
            }
            scan = malwareScanner.scan(bucket, req.getObjectPath());
        }

        HealthRecord record = new HealthRecord();
        record.setUserId(ownerUserId);
        record.setCategory(req.getCategory());
        record.setOriginalFileName(req.getOriginalFileName());
        record.setMimeType(req.getMimeType());
        record.setSizeBytes(sizeBytes);
        record.setStorageBucket(bucket);
        record.setStorageObjectPath(req.getObjectPath());
        record.setStorageProvider(backend);
        record.setKind(pending.getKind());
        record.setStatus(RecordStatus.UPLOADED);
        record.setScanStatus(scan);
        if (pending.getKind() == FileKind.VIDEO) {
            record.setRemovalAt(rules.removalAt(created));
        }
        HealthRecord saved = healthRecordRepository.save(record);
        pendingUploadRepository.delete(pending);

        logAccess(saved.getId(), RecordAccessAction.UPLOAD, ownerUserId, RecordAccessRelationship.OWNER, null);
        auditService.record("RECORD_UPLOADED", "HealthRecord", String.valueOf(saved.getId()), null,
                Map.of("category", saved.getCategory().name(), "mimeType", saved.getMimeType(),
                        "sizeBytes", saved.getSizeBytes(), "scanStatus", saved.getScanStatus().name(),
                        "kind", saved.getKind().name(), "storageProvider", saved.getStorageProvider().name(),
                        "malwareScanning", backend == StorageBackend.S3 && !scanPolicy.required() ? "OFF" : "ON"));
        if (backend == StorageBackend.S3 && !scanPolicy.required()) {
            log.warn("Record {} saved without a malware scan: scanning is off (pre-production only)", saved.getId());
        }

        return saved;
    }

    // ---- Reading ----

    public Optional<HealthRecord> find(Long id) {
        return healthRecordRepository.findById(id);
    }

    /** Her files, including videos in trash (shown as unavailable) until S3 deletes them for good. */
    public List<HealthRecord> listForOwner(Long ownerUserId) {
        LocalDateTime now = LocalDateTime.now();
        return healthRecordRepository.findByUserIdAndStatusNotOrderByCreatedAtDesc(ownerUserId, RecordStatus.DELETED).stream()
                .filter(r -> !rules.isGone(r, now))
                .map(this::refreshScanStatus)
                .toList();
    }

    /** Union of direct PROVIDER shares and BOOKING shares whose booking's providerId matches. Trashed videos are left out. */
    public List<HealthRecord> listSharedWithProvider(Long providerId) {
        LocalDateTime now = LocalDateTime.now();

        List<Long> directRecordIds = recordShareRepository.findByTargetProviderIdAndRevokedAtIsNull(providerId).stream()
                .filter(share -> share.getExpiresAt().isAfter(now))
                .map(RecordShare::getRecordId)
                .toList();

        List<Long> providerBookingIds = bookingRepository.findByProviderIdOrderByCreatedAtDesc(providerId).stream()
                .map(Booking::getId)
                .toList();
        List<Long> viaBookingRecordIds = providerBookingIds.isEmpty()
                ? List.of()
                : recordShareRepository.findByTargetBookingIdInAndRevokedAtIsNull(providerBookingIds).stream()
                        .filter(share -> share.getExpiresAt().isAfter(now))
                        .map(RecordShare::getRecordId)
                        .toList();

        Set<Long> recordIds = new java.util.LinkedHashSet<>();
        recordIds.addAll(directRecordIds);
        recordIds.addAll(viaBookingRecordIds);

        return recordIds.stream()
                .map(healthRecordRepository::findById)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(r -> r.getStatus() != RecordStatus.DELETED)
                .filter(r -> rules.trashedAt(r, now) == null)
                .map(this::refreshScanStatus)
                .toList();
    }

    /**
     * S3 files start PENDING; GuardDuty writes its verdict as an object tag. Read it lazily whenever the
     * record is looked at, until it is final. Supabase files are left alone (NOT_SCANNED, F9-S6).
     */
    public HealthRecord refreshScanStatus(HealthRecord record) {
        if (record.getStorageProvider() != StorageBackend.S3 || record.getScanStatus() != ScanVerdict.PENDING
                || record.getStatus() == RecordStatus.DELETED || storage.s3().isEmpty()) {
            return record;
        }
        ScanVerdict verdict = readScanQuietly(storage.requireS3(), record.getStorageBucket(),
                record.getStorageObjectPath(), ScanVerdict.PENDING);
        if (verdict == ScanVerdict.PENDING) {
            return record;
        }
        record.setScanStatus(verdict);
        HealthRecord saved = healthRecordRepository.save(record);
        auditService.record(verdict == ScanVerdict.CLEAN ? "RECORD_SCAN_CLEAN" : "RECORD_SCAN_BLOCKED",
                "HealthRecord", String.valueOf(saved.getId()), null, Map.of("scanStatus", verdict.name()));
        if (verdict != ScanVerdict.CLEAN) {
            log.warn("Record {} blocked after malware scan: {}", saved.getId(), verdict);
        }
        return saved;
    }

    private ScanVerdict readScanQuietly(S3StorageProvider s3, String bucket, String objectPath, ScanVerdict fallback) {
        try {
            return s3.readScanStatus(bucket, objectPath);
        } catch (Exception e) {
            log.warn("Couldn't read the malware scan tag yet: {}", e.getClass().getSimpleName());
            return fallback;
        }
    }

    /** What the UI needs to show a file: whether it can be opened now, and the video's removal dates. */
    public RecordState stateOf(HealthRecord record) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime trashedAt = rules.trashedAt(record, now);
        boolean available = record.getStatus() != RecordStatus.DELETED && trashedAt == null && scanPolicy.allowsDownload(record);
        return new RecordState(available, record.getRemovalAt(), trashedAt,
                trashedAt != null ? rules.permanentDeletionAt(record) : null, rules.warnFrom(record));
    }

    public HealthRecord patchCategory(HealthRecord record, com.narturebelle.model.RecordCategory newCategory, Long actorUserId) {
        var oldCategory = record.getCategory();
        record.setCategory(newCategory);
        HealthRecord saved = healthRecordRepository.save(record);

        auditService.record("RECORD_UPDATED", "HealthRecord", String.valueOf(saved.getId()), null,
                Map.of("oldCategory", oldCategory.name(), "newCategory", newCategory.name()));
        return saved;
    }

    /**
     * Short-lived download link. S3 files must be scanned CLEAN and not in trash (the bucket policy
     * enforces the scan rule too). disposition "attachment" makes phones save the file under a generic
     * name, so the original file name never appears in S3 logs; anything else plays inline.
     */
    public RecordDownloadIntent issueDownloadUrl(HealthRecord record, Long actorUserId,
                                                  RecordAccessRelationship relationship, Long matchedShareId,
                                                  String disposition) {
        refreshScanStatus(record);
        if (record.getStatus() == RecordStatus.DELETED) {
            throw new RecordFileException(HttpStatus.CONFLICT, "FILE_DELETED", "This file was deleted.");
        }
        if (rules.trashedAt(record, LocalDateTime.now()) != null) {
            throw new RecordFileException(HttpStatus.CONFLICT, "FILE_IN_TRASH",
                    "This video has been removed. Contact support if you need it back.");
        }

        SignedDownloadUrl signed;
        if (record.getStorageProvider() == StorageBackend.S3) {
            if (record.getScanStatus() == ScanVerdict.INFECTED || record.getScanStatus() == ScanVerdict.SCAN_FAILED) {
                throw new RecordFileException(HttpStatus.CONFLICT, "FILE_BLOCKED",
                        "This file can't be opened because it didn't pass the safety check.");
            }
            if (!scanPolicy.allowsDownload(record)) {
                throw RecordFileException.notReady();
            }
            String contentDisposition = "attachment".equalsIgnoreCase(disposition)
                    ? "attachment; filename=\"NartureBelle-" + record.getKind().name().toLowerCase() + "-"
                        + record.getId() + "." + rules.extensionFor(record.getMimeType()) + "\""
                    : "inline";
            signed = storage.requireS3().createSignedDownloadUrl(record.getStorageBucket(), record.getStorageObjectPath(),
                    DOWNLOAD_LINK_TTL, record.getMimeType(), contentDisposition);
        } else {
            signed = storage.provider(StorageBackend.SUPABASE).createSignedDownloadUrl(
                    record.getStorageBucket(), record.getStorageObjectPath(), DOWNLOAD_LINK_TTL);
        }

        logAccess(record.getId(), RecordAccessAction.DOWNLOAD, actorUserId, relationship, matchedShareId);
        return new RecordDownloadIntent(signed.downloadUrl(), signed.expiresAt());
    }

    public void logView(HealthRecord record, Long actorUserId, RecordAccessRelationship relationship, Long matchedShareId) {
        logAccess(record.getId(), RecordAccessAction.VIEW, actorUserId, relationship, matchedShareId);
    }

    public HealthRecord softDelete(HealthRecord record, Long actorUserId) {
        record.setStatus(RecordStatus.DELETED);
        HealthRecord saved = healthRecordRepository.save(record);

        LocalDateTime now = LocalDateTime.now();
        for (RecordShare share : recordShareRepository.findByRecordIdAndRevokedAtIsNull(record.getId())) {
            share.setRevokedAt(now);
            share.setRevokedByUserId(actorUserId);
            recordShareRepository.save(share);
        }

        try {
            storage.provider(record.getStorageProvider()).deleteObject(record.getStorageBucket(), record.getStorageObjectPath());
        } catch (Exception bestEffortFailure) {
            // Best-effort — never block the DB-level delete on a storage-side failure.
        }

        logAccess(saved.getId(), RecordAccessAction.DELETE, actorUserId, RecordAccessRelationship.OWNER, null);
        auditService.record("RECORD_DELETED", "HealthRecord", String.valueOf(saved.getId()), null,
                Map.of("category", saved.getCategory().name()));
        return saved;
    }

    /**
     * Support restores a trashed video on the mother's request (F9-S19 item 13). It gets a fresh 30 days
     * and is scanned again. Audited with the reason; no health details belong in the reason.
     */
    public HealthRecord restoreVideo(HealthRecord record, String reason) {
        if (record.getKind() != FileKind.VIDEO || record.getStorageProvider() != StorageBackend.S3) {
            throw new RecordNotAllowedException("Only videos stored on S3 can be restored from trash.");
        }
        if (record.getStatus() == RecordStatus.DELETED) {
            throw new RecordNotAllowedException("The mother deleted this video herself; it can't be restored here.");
        }
        S3StorageProvider s3 = storage.requireS3();
        StoredObjectInfo info = s3.restoreLatestVersion(record.getStorageBucket(), record.getStorageObjectPath(), record.getMimeType())
                .orElseThrow(() -> new RecordFileException(HttpStatus.CONFLICT, "VIDEO_GONE",
                        "This video's trash period has ended and it was deleted permanently."));
        record.setRemovalAt(rules.removalAt(info.lastModified() != null ? info.lastModified() : Instant.now()));
        record.setScanStatus(ScanVerdict.PENDING);
        HealthRecord saved = healthRecordRepository.save(record);

        Map<String, Object> meta = new HashMap<>();
        meta.put("removalAt", saved.getRemovalAt().toString());
        auditService.record("RECORD_VIDEO_RESTORED", "HealthRecord", String.valueOf(saved.getId()), reason, meta);
        return saved;
    }

    // ---- Shares ----

    public RecordShare createShare(HealthRecord record, Long actorUserId, CreateRecordShareRequest req) {
        boolean hasProvider = req.getTargetProviderId() != null;
        boolean hasBooking = req.getTargetBookingId() != null;
        if (hasProvider == hasBooking) {
            throw new InvalidRecordRequestException("shareTargetType", "Exactly one of targetProviderId or targetBookingId must be set.");
        }
        if (req.getShareTargetType() == ShareTargetType.BOOKING) {
            Booking booking = bookingRepository.findById(req.getTargetBookingId())
                    .orElseThrow(() -> new InvalidRecordRequestException("targetBookingId", "No such booking."));
            if (!booking.getUserId().equals(record.getUserId())) {
                throw new InvalidRecordRequestException("targetBookingId", "That booking does not belong to this record's owner.");
            }
        }
        if (!req.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw new InvalidRecordRequestException("expiresAt", "Expiry must be in the future.");
        }
        if (req.getExpiresAt().isAfter(LocalDateTime.now().plusDays(maxShareExpiryDays))) {
            throw new InvalidRecordRequestException("expiresAt", "Expiry may not be more than " + maxShareExpiryDays + " days out.");
        }
        // Sharing doesn't keep a video longer (D49 open question; safe default): a share can't outlast it.
        if (record.getRemovalAt() != null && req.getExpiresAt().isAfter(record.getRemovalAt())) {
            throw new InvalidRecordRequestException("expiresAt", "This video is removed on " + record.getRemovalAt()
                    + ", so the share must end by then.");
        }

        RecordShare share = new RecordShare();
        share.setRecordId(record.getId());
        share.setGrantedByUserId(actorUserId);
        share.setShareTargetType(req.getShareTargetType());
        share.setTargetProviderId(req.getTargetProviderId());
        share.setTargetBookingId(req.getTargetBookingId());
        share.setExpiresAt(req.getExpiresAt());
        RecordShare saved = recordShareRepository.save(share);

        logAccess(record.getId(), RecordAccessAction.SHARE, actorUserId, RecordAccessRelationship.OWNER, saved.getId());
        auditService.record("RECORD_SHARE_CREATED", "HealthRecord", String.valueOf(record.getId()), null,
                Map.of("shareTargetType", saved.getShareTargetType().name(),
                        "targetId", hasProvider ? saved.getTargetProviderId() : saved.getTargetBookingId(),
                        "expiresAt", saved.getExpiresAt().toString()));
        return saved;
    }

    public RecordShare revokeShare(RecordShare share, Long actorUserId, String reason) {
        if (share.getRevokedAt() != null) {
            throw new RecordNotAllowedException("This share was already revoked.");
        }
        share.setRevokedAt(LocalDateTime.now());
        share.setRevokedByUserId(actorUserId);
        RecordShare saved = recordShareRepository.save(share);

        logAccess(share.getRecordId(), RecordAccessAction.REVOKE_SHARE, actorUserId, RecordAccessRelationship.OWNER, saved.getId());
        auditService.record("RECORD_SHARE_REVOKED", "HealthRecord", String.valueOf(share.getRecordId()), reason, null);
        return saved;
    }

    public List<RecordShare> listShares(Long recordId) {
        return recordShareRepository.findByRecordIdOrderByCreatedAtDesc(recordId);
    }

    public List<RecordAccessEvent> listAccessLog(Long recordId) {
        return recordAccessEventRepository.findByRecordIdOrderByOccurredAtDesc(recordId);
    }

    public Optional<RecordShare> findShare(Long shareId) {
        return recordShareRepository.findById(shareId);
    }

    private void logAccess(Long recordId, RecordAccessAction action, Long actorUserId,
                            RecordAccessRelationship relationship, Long shareId) {
        RecordAccessEvent event = new RecordAccessEvent();
        event.setRecordId(recordId);
        event.setAction(action);
        event.setActorUserId(actorUserId);
        event.setActorRelationship(relationship);
        event.setShareId(shareId);
        recordAccessEventRepository.save(event);
    }

    public record RecordUploadIntent(String mode, String objectPath, String uploadUrl, String token,
                                     LocalDateTime expiresAt, Map<String, String> headers,
                                     String uploadId, Long partSizeBytes, Integer partCount) {
        static RecordUploadIntent single(String objectPath, String uploadUrl, String token, LocalDateTime expiresAt,
                                         Map<String, String> headers) {
            return new RecordUploadIntent("SINGLE", objectPath, uploadUrl, token, expiresAt, headers, null, null, null);
        }

        static RecordUploadIntent multipart(String objectPath, String uploadId, long partSizeBytes, int partCount,
                                            LocalDateTime expiresAt) {
            return new RecordUploadIntent("MULTIPART", objectPath, null, null, expiresAt, null, uploadId, partSizeBytes, partCount);
        }
    }

    public record RecordDownloadIntent(String downloadUrl, LocalDateTime expiresAt) {}
    public record PartLink(int partNumber, String url, LocalDateTime expiresAt) {}
    public record KindRule(FileKind kind, List<String> mimeTypes, List<String> extensions, long maxBytes) {}
    public record UploadRules(List<KindRule> kinds, long quotaBytes, long usedBytes) {}
    public record RecordState(boolean available, LocalDateTime removalAt, LocalDateTime trashedAt,
                              LocalDateTime permanentDeletionAt, LocalDateTime warnFrom) {}
}
