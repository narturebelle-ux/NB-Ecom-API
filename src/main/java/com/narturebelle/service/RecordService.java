package com.narturebelle.service;

import com.narturebelle.dto.CreateRecordShareRequest;
import com.narturebelle.dto.CreateRecordUploadUrlRequest;
import com.narturebelle.dto.RegisterRecordRequest;
import com.narturebelle.exception.InvalidRecordRequestException;
import com.narturebelle.exception.RecordNotAllowedException;
import com.narturebelle.exception.RecordUploadNotConfirmedException;
import com.narturebelle.model.Booking;
import com.narturebelle.model.HealthRecord;
import com.narturebelle.model.RecordAccessAction;
import com.narturebelle.model.RecordAccessEvent;
import com.narturebelle.model.RecordAccessRelationship;
import com.narturebelle.model.RecordShare;
import com.narturebelle.model.RecordStatus;
import com.narturebelle.model.ScanVerdict;
import com.narturebelle.model.ShareTargetType;
import com.narturebelle.repository.BookingRepository;
import com.narturebelle.repository.HealthRecordRepository;
import com.narturebelle.repository.RecordAccessEventRepository;
import com.narturebelle.repository.RecordShareRepository;
import com.narturebelle.security.AuditService;
import com.narturebelle.storage.MalwareScanner;
import com.narturebelle.storage.SignedDownloadUrl;
import com.narturebelle.storage.SignedUploadUrl;
import com.narturebelle.storage.StorageProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Component
public class RecordService {

    private final HealthRecordRepository healthRecordRepository;
    private final RecordShareRepository recordShareRepository;
    private final RecordAccessEventRepository recordAccessEventRepository;
    private final BookingRepository bookingRepository;
    private final StorageProvider storageProvider;
    private final MalwareScanner malwareScanner;
    private final AuditService auditService;
    private final String bucket;
    private final Set<String> allowedMimeTypes;
    private final long maxSizeBytes;
    private final long maxShareExpiryDays;

    public RecordService(HealthRecordRepository healthRecordRepository, RecordShareRepository recordShareRepository,
                          RecordAccessEventRepository recordAccessEventRepository, BookingRepository bookingRepository,
                          StorageProvider storageProvider, MalwareScanner malwareScanner, AuditService auditService,
                          @Value("${supabase.storage.bucket}") String bucket,
                          @Value("${records.upload.allowed-mime-types}") String allowedMimeTypesCsv,
                          @Value("${records.upload.max-size-bytes}") long maxSizeBytes,
                          @Value("${records.share.max-expiry-days}") long maxShareExpiryDays) {
        this.healthRecordRepository = healthRecordRepository;
        this.recordShareRepository = recordShareRepository;
        this.recordAccessEventRepository = recordAccessEventRepository;
        this.bookingRepository = bookingRepository;
        this.storageProvider = storageProvider;
        this.malwareScanner = malwareScanner;
        this.auditService = auditService;
        this.bucket = bucket;
        this.allowedMimeTypes = Set.of(allowedMimeTypesCsv.split(","));
        this.maxSizeBytes = maxSizeBytes;
        this.maxShareExpiryDays = maxShareExpiryDays;
    }

    public RecordUploadIntent issueUploadUrl(Long ownerUserId, CreateRecordUploadUrlRequest req) {
        if (!allowedMimeTypes.contains(req.getMimeType())) {
            throw new InvalidRecordRequestException("mimeType", "File type not allowed: " + req.getMimeType());
        }
        if (req.getSizeBytes() > maxSizeBytes) {
            throw new InvalidRecordRequestException("sizeBytes", "File exceeds the maximum allowed size of " + maxSizeBytes + " bytes.");
        }

        String objectPath = "records/" + ownerUserId + "/" + UUID.randomUUID();
        SignedUploadUrl signed = storageProvider.createSignedUploadUrl(bucket, objectPath, Duration.ofMinutes(15));
        return new RecordUploadIntent(objectPath, signed.uploadUrl(), signed.token(), signed.expiresAt());
    }

    public HealthRecord registerUpload(Long ownerUserId, RegisterRecordRequest req) {
        if (!storageProvider.objectExists(bucket, req.getObjectPath())) {
            throw new RecordUploadNotConfirmedException(
                    "No uploaded object found at the given path — upload to the signed URL before registering.");
        }

        HealthRecord record = new HealthRecord();
        record.setUserId(ownerUserId);
        record.setCategory(req.getCategory());
        record.setOriginalFileName(req.getOriginalFileName());
        record.setMimeType(req.getMimeType());
        record.setSizeBytes(req.getSizeBytes());
        record.setStorageBucket(bucket);
        record.setStorageObjectPath(req.getObjectPath());
        record.setStatus(RecordStatus.UPLOADED);
        record.setScanStatus(malwareScanner.scan(bucket, req.getObjectPath()));
        HealthRecord saved = healthRecordRepository.save(record);

        logAccess(saved.getId(), RecordAccessAction.UPLOAD, ownerUserId, RecordAccessRelationship.OWNER, null);
        auditService.record("RECORD_UPLOADED", "HealthRecord", String.valueOf(saved.getId()), null,
                Map.of("category", saved.getCategory().name(), "mimeType", saved.getMimeType(),
                        "sizeBytes", saved.getSizeBytes(), "scanStatus", saved.getScanStatus().name()));

        return saved;
    }

    public Optional<HealthRecord> find(Long id) {
        return healthRecordRepository.findById(id);
    }

    public List<HealthRecord> listForOwner(Long ownerUserId) {
        return healthRecordRepository.findByUserIdAndStatusNotOrderByCreatedAtDesc(ownerUserId, RecordStatus.DELETED);
    }

    /** Union of direct PROVIDER shares and BOOKING shares whose booking's providerId matches. */
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
                .toList();
    }

    public HealthRecord patchCategory(HealthRecord record, com.narturebelle.model.RecordCategory newCategory, Long actorUserId) {
        var oldCategory = record.getCategory();
        record.setCategory(newCategory);
        HealthRecord saved = healthRecordRepository.save(record);

        auditService.record("RECORD_UPDATED", "HealthRecord", String.valueOf(saved.getId()), null,
                Map.of("oldCategory", oldCategory.name(), "newCategory", newCategory.name()));
        return saved;
    }

    public RecordDownloadIntent issueDownloadUrl(HealthRecord record, Long actorUserId,
                                                  RecordAccessRelationship relationship, Long matchedShareId) {
        SignedDownloadUrl signed = storageProvider.createSignedDownloadUrl(
                record.getStorageBucket(), record.getStorageObjectPath(), Duration.ofMinutes(10));

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
            storageProvider.deleteObject(record.getStorageBucket(), record.getStorageObjectPath());
        } catch (Exception bestEffortFailure) {
            // Best-effort — never block the DB-level delete on a storage-side failure.
        }

        logAccess(saved.getId(), RecordAccessAction.DELETE, actorUserId, RecordAccessRelationship.OWNER, null);
        auditService.record("RECORD_DELETED", "HealthRecord", String.valueOf(saved.getId()), null,
                Map.of("category", saved.getCategory().name()));
        return saved;
    }

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

    public record RecordUploadIntent(String objectPath, String uploadUrl, String token, LocalDateTime expiresAt) {}
    public record RecordDownloadIntent(String downloadUrl, LocalDateTime expiresAt) {}
}
