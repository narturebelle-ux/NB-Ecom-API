package com.narturebelle.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Something the daily video job has done for one video, for one removal date (F9-S19 items 12, 13).
 * Unique per (record, kind, removal date), so each warning or audit event happens once. A restored video
 * has a new removal date, so its warnings and audit events start again.
 * kind is WARN_<days before> (WARN_7, WARN_1 by default; the days are config), TRASHED or PURGED.
 */
@Entity
@Table(name = "video_retention_notices",
        uniqueConstraints = @UniqueConstraint(name = "uq_video_retention_notice",
                columnNames = {"record_id", "kind", "cycle_removal_at"}))
public class VideoRetentionNotice {

    public static final String TRASHED = "TRASHED";
    public static final String PURGED = "PURGED";

    public static String warning(int daysBefore) {
        return "WARN_" + daysBefore;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "record_id", nullable = false)
    private Long recordId;

    @Column(nullable = false, length = 32)
    private String kind;

    @Column(name = "cycle_removal_at", nullable = false)
    private LocalDateTime cycleRemovalAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected VideoRetentionNotice() {}

    public VideoRetentionNotice(Long recordId, String kind, LocalDateTime cycleRemovalAt, LocalDateTime createdAt) {
        this.recordId = recordId;
        this.kind = kind;
        this.cycleRemovalAt = cycleRemovalAt;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Long getRecordId() { return recordId; }
    public String getKind() { return kind; }
    public LocalDateTime getCycleRemovalAt() { return cycleRemovalAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
