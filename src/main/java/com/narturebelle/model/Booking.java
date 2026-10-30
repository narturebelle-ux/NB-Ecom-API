package com.narturebelle.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * A booked slot AND its session lifecycle in one entity — a session only ever exists in the context
 * of exactly one booking, so a 1:1 split would just add a join with no independent lifecycle.
 * Reschedule never mutates this row's slot/time in place: it cancels this one (RESCHEDULED, not
 * CANCELLED) and a new Booking row is created referencing it via rescheduledFromBookingId.
 */
@Entity
@Table(name = "bookings")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long providerId;

    @Column(nullable = false)
    private Long serviceOfferingId;

    @Column(nullable = false)
    private Long slotId;

    /** Denormalized from the provider at booking time — same pattern as Order.price denormalizing PlanVersion. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role professionalType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingStatus status;

    @Column(nullable = false)
    private LocalDateTime scheduledStart;

    @Column(nullable = false)
    private LocalDateTime scheduledEnd;

    @Column(columnDefinition = "text")
    private String providerNote;

    private LocalDateTime noteLockedAt;

    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime noShowAt;
    private LocalDateTime cancelledAt;

    private Long cancelledByUserId;
    @Column(columnDefinition = "text")
    private String cancelReason;

    private Long noShowRecordedByUserId;
    @Column(columnDefinition = "text")
    private String noShowReason;

    private Long rescheduledFromBookingId;

    private Long consumedEntitlementId;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Version
    private Long version;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (this.createdAt == null) this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Booking() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Long getProviderId() { return providerId; }
    public void setProviderId(Long providerId) { this.providerId = providerId; }

    public Long getServiceOfferingId() { return serviceOfferingId; }
    public void setServiceOfferingId(Long serviceOfferingId) { this.serviceOfferingId = serviceOfferingId; }

    public Long getSlotId() { return slotId; }
    public void setSlotId(Long slotId) { this.slotId = slotId; }

    public Role getProfessionalType() { return professionalType; }
    public void setProfessionalType(Role professionalType) { this.professionalType = professionalType; }

    public BookingStatus getStatus() { return status; }
    public void setStatus(BookingStatus status) { this.status = status; }

    public LocalDateTime getScheduledStart() { return scheduledStart; }
    public void setScheduledStart(LocalDateTime scheduledStart) { this.scheduledStart = scheduledStart; }

    public LocalDateTime getScheduledEnd() { return scheduledEnd; }
    public void setScheduledEnd(LocalDateTime scheduledEnd) { this.scheduledEnd = scheduledEnd; }

    public String getProviderNote() { return providerNote; }
    public void setProviderNote(String providerNote) { this.providerNote = providerNote; }

    public LocalDateTime getNoteLockedAt() { return noteLockedAt; }
    public void setNoteLockedAt(LocalDateTime noteLockedAt) { this.noteLockedAt = noteLockedAt; }

    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }

    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }

    public LocalDateTime getNoShowAt() { return noShowAt; }
    public void setNoShowAt(LocalDateTime noShowAt) { this.noShowAt = noShowAt; }

    public LocalDateTime getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(LocalDateTime cancelledAt) { this.cancelledAt = cancelledAt; }

    public Long getCancelledByUserId() { return cancelledByUserId; }
    public void setCancelledByUserId(Long cancelledByUserId) { this.cancelledByUserId = cancelledByUserId; }

    public String getCancelReason() { return cancelReason; }
    public void setCancelReason(String cancelReason) { this.cancelReason = cancelReason; }

    public Long getNoShowRecordedByUserId() { return noShowRecordedByUserId; }
    public void setNoShowRecordedByUserId(Long noShowRecordedByUserId) { this.noShowRecordedByUserId = noShowRecordedByUserId; }

    public String getNoShowReason() { return noShowReason; }
    public void setNoShowReason(String noShowReason) { this.noShowReason = noShowReason; }

    public Long getRescheduledFromBookingId() { return rescheduledFromBookingId; }
    public void setRescheduledFromBookingId(Long rescheduledFromBookingId) { this.rescheduledFromBookingId = rescheduledFromBookingId; }

    public Long getConsumedEntitlementId() { return consumedEntitlementId; }
    public void setConsumedEntitlementId(Long consumedEntitlementId) { this.consumedEntitlementId = consumedEntitlementId; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
