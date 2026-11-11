package com.narturebelle.service;

import com.narturebelle.exception.InvalidRecordRequestException;
import com.narturebelle.model.FileKind;
import com.narturebelle.model.HealthRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Per-type file rules and video retention dates (F9-S19, D49), all from config (application.yml,
 * records.*). Retention dates copy how S3 lifecycle counts: object creation time plus N days, rounded
 * up to the next midnight UTC. So the app hides a video at the same moment S3 moves it to trash.
 */
@Component
public class RecordFileRules {

    private static final long S3_MIN_PART_BYTES = 5L * 1024 * 1024;
    private static final int S3_MAX_PARTS = 10_000;

    /** Extensions the UI can offer in its file picker, per MIME type. */
    private static final Map<String, List<String>> EXTENSIONS = Map.ofEntries(
            Map.entry("application/pdf", List.of("pdf")),
            Map.entry("image/jpeg", List.of("jpg", "jpeg")),
            Map.entry("image/png", List.of("png")),
            Map.entry("image/heic", List.of("heic")),
            Map.entry("video/mp4", List.of("mp4")),
            Map.entry("video/quicktime", List.of("mov")),
            Map.entry("video/webm", List.of("webm")),
            Map.entry("video/3gpp", List.of("3gp")));

    private final Set<String> documentMimeTypes;
    private final long documentMaxBytes;
    private final Set<String> blockedMimeTypes;
    private final long quotaBytes;
    private final Set<String> videoMimeTypes;
    private final long videoMaxBytes;
    private final long multipartThresholdBytes;
    private final long partSizeBytes;
    private final int retentionDays;
    private final int trashDays;
    private final List<Integer> warnDaysBefore;

    public RecordFileRules(@Value("${records.upload.allowed-mime-types}") String documentMimeTypes,
                           @Value("${records.upload.max-size-bytes}") long documentMaxBytes,
                           @Value("${records.upload.blocked-mime-types:}") String blockedMimeTypes,
                           @Value("${records.upload.quota-bytes-per-user:10737418240}") long quotaBytes,
                           @Value("${records.video.allowed-mime-types:video/mp4,video/quicktime,video/webm,video/3gpp}") String videoMimeTypes,
                           @Value("${records.video.max-size-bytes:2147483648}") long videoMaxBytes,
                           @Value("${records.video.multipart-threshold-bytes:67108864}") long multipartThresholdBytes,
                           @Value("${records.video.part-size-bytes:16777216}") long partSizeBytes,
                           @Value("${records.video.retention-days:30}") int retentionDays,
                           @Value("${records.video.trash-days:7}") int trashDays,
                           @Value("${records.video.warn-days-before:7,1}") String warnDaysBefore) {
        this.documentMimeTypes = csv(documentMimeTypes);
        this.documentMaxBytes = documentMaxBytes;
        this.blockedMimeTypes = csv(blockedMimeTypes);
        this.quotaBytes = quotaBytes;
        this.videoMimeTypes = csv(videoMimeTypes);
        this.videoMaxBytes = videoMaxBytes;
        this.multipartThresholdBytes = multipartThresholdBytes;
        this.partSizeBytes = Math.max(partSizeBytes, S3_MIN_PART_BYTES);
        this.retentionDays = retentionDays;
        this.trashDays = trashDays;
        this.warnDaysBefore = csv(warnDaysBefore).stream().map(Integer::valueOf)
                .sorted((a, b) -> b - a).toList();
    }

    /** DOCUMENT or VIDEO for this MIME type, or a 400 if it isn't accepted. Blocked types win over allowed ones. */
    public FileKind kindFor(String mimeType) {
        if (blockedMimeTypes.contains(mimeType)) {
            throw new InvalidRecordRequestException("mimeType", "Word documents can't be uploaded yet. Please save it as a PDF.");
        }
        if (videoMimeTypes.contains(mimeType)) return FileKind.VIDEO;
        if (documentMimeTypes.contains(mimeType)) return FileKind.DOCUMENT;
        throw new InvalidRecordRequestException("mimeType", "File type not allowed: " + mimeType);
    }

    public long maxBytes(FileKind kind) {
        return kind == FileKind.VIDEO ? videoMaxBytes : documentMaxBytes;
    }

    public Set<String> mimeTypes(FileKind kind) {
        return kind == FileKind.VIDEO ? videoMimeTypes : documentMimeTypes;
    }

    public List<String> extensions(FileKind kind) {
        return mimeTypes(kind).stream()
                .flatMap(m -> EXTENSIONS.getOrDefault(m, List.of()).stream())
                .distinct().toList();
    }

    public String extensionFor(String mimeType) {
        return EXTENSIONS.getOrDefault(mimeType, List.of("bin")).get(0);
    }

    public long quotaBytes() {
        return quotaBytes;
    }

    public boolean needsMultipart(FileKind kind, long sizeBytes) {
        return kind == FileKind.VIDEO && sizeBytes > multipartThresholdBytes;
    }

    /** Configured part size, raised if needed so the file fits in S3's 10,000 parts. */
    public long partSizeFor(long sizeBytes) {
        long minForCount = (sizeBytes + S3_MAX_PARTS - 1) / S3_MAX_PARTS;
        return Math.max(partSizeBytes, minForCount);
    }

    // ---- Video retention (D49) ----

    /** When S3's lifecycle rule moves a video created at this instant to trash. */
    public LocalDateTime removalAt(Instant objectCreated) {
        Instant due = objectCreated.plus(retentionDays, ChronoUnit.DAYS);
        Instant midnight = due.atOffset(ZoneOffset.UTC).truncatedTo(ChronoUnit.DAYS).toInstant();
        Instant rounded = midnight.equals(due) ? due : midnight.plus(1, ChronoUnit.DAYS);
        return LocalDateTime.ofInstant(rounded, ZoneId.systemDefault());
    }

    /** Set once a video has reached its removal date: it is in trash and can't be downloaded. */
    public LocalDateTime trashedAt(HealthRecord record, LocalDateTime now) {
        LocalDateTime removalAt = record.getRemovalAt();
        return removalAt != null && !now.isBefore(removalAt) ? removalAt : null;
    }

    /** Earliest moment S3 deletes the trashed version for good. */
    public LocalDateTime permanentDeletionAt(HealthRecord record) {
        return record.getRemovalAt() == null ? null : record.getRemovalAt().plusDays(trashDays);
    }

    /** Past trash too: nothing is left in S3, so the record is no longer shown. */
    public boolean isGone(HealthRecord record, LocalDateTime now) {
        LocalDateTime gone = permanentDeletionAt(record);
        return gone != null && !now.isBefore(gone);
    }

    /** The first warning (day 23 of 30 by default); the UI shows the download banner from here. */
    public LocalDateTime warnFrom(HealthRecord record) {
        if (record.getRemovalAt() == null || warnDaysBefore.isEmpty()) return null;
        return record.getRemovalAt().minusDays(warnDaysBefore.get(0));
    }

    /** Largest first, e.g. [7, 1]. */
    public List<Integer> warnDaysBefore() {
        return warnDaysBefore;
    }

    public int trashDays() {
        return trashDays;
    }

    private static Set<String> csv(String value) {
        if (value == null || value.isBlank()) return Set.of();
        return Arrays.stream(value.split(",")).map(String::trim).filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
