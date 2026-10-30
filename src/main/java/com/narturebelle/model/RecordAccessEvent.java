package com.narturebelle.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * The strict append-only access log. Deliberately has NO column for filename, mime type, size, or
 * category — "no document names or health data in logs" is enforced structurally here, not just by
 * convention, because there is nowhere to put a sensitive string even by mistake. Stricter than
 * AuditEvent, since this is the first phase where audit-shaped data becomes directly visible to a
 * non-admin end user (GET /records/{id}/access-log).
 */
@Entity
@Table(name = "record_access_events")
public class RecordAccessEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long recordId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RecordAccessAction action;

    @Column(nullable = false)
    private Long actorUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RecordAccessRelationship actorRelationship;

    private Long shareId;

    @Column(nullable = false)
    private LocalDateTime occurredAt;

    @PrePersist
    protected void onCreate() {
        if (this.occurredAt == null) {
            this.occurredAt = LocalDateTime.now();
        }
    }

    public RecordAccessEvent() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getRecordId() { return recordId; }
    public void setRecordId(Long recordId) { this.recordId = recordId; }

    public RecordAccessAction getAction() { return action; }
    public void setAction(RecordAccessAction action) { this.action = action; }

    public Long getActorUserId() { return actorUserId; }
    public void setActorUserId(Long actorUserId) { this.actorUserId = actorUserId; }

    public RecordAccessRelationship getActorRelationship() { return actorRelationship; }
    public void setActorRelationship(RecordAccessRelationship actorRelationship) { this.actorRelationship = actorRelationship; }

    public Long getShareId() { return shareId; }
    public void setShareId(Long shareId) { this.shareId = shareId; }

    public LocalDateTime getOccurredAt() { return occurredAt; }
    public void setOccurredAt(LocalDateTime occurredAt) { this.occurredAt = occurredAt; }
}
