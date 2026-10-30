package com.narturebelle.dto;

import com.narturebelle.model.Booking;
import com.narturebelle.model.BookingStatus;
import com.narturebelle.model.Role;

import java.time.LocalDateTime;

public class BookingResponse {

    private Long id;
    private Long userId;
    private Long providerId;
    private Long serviceOfferingId;
    private Long slotId;
    private Role professionalType;
    private BookingStatus status;
    private LocalDateTime scheduledStart;
    private LocalDateTime scheduledEnd;
    private String providerNote;
    private LocalDateTime noteLockedAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime noShowAt;
    private LocalDateTime cancelledAt;
    private String cancelReason;
    private Long rescheduledFromBookingId;
    private LocalDateTime createdAt;

    public static BookingResponse from(Booking booking) {
        BookingResponse dto = new BookingResponse();
        dto.id = booking.getId();
        dto.userId = booking.getUserId();
        dto.providerId = booking.getProviderId();
        dto.serviceOfferingId = booking.getServiceOfferingId();
        dto.slotId = booking.getSlotId();
        dto.professionalType = booking.getProfessionalType();
        dto.status = booking.getStatus();
        dto.scheduledStart = booking.getScheduledStart();
        dto.scheduledEnd = booking.getScheduledEnd();
        dto.providerNote = booking.getProviderNote();
        dto.noteLockedAt = booking.getNoteLockedAt();
        dto.startedAt = booking.getStartedAt();
        dto.completedAt = booking.getCompletedAt();
        dto.noShowAt = booking.getNoShowAt();
        dto.cancelledAt = booking.getCancelledAt();
        dto.cancelReason = booking.getCancelReason();
        dto.rescheduledFromBookingId = booking.getRescheduledFromBookingId();
        dto.createdAt = booking.getCreatedAt();
        return dto;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getProviderId() { return providerId; }
    public Long getServiceOfferingId() { return serviceOfferingId; }
    public Long getSlotId() { return slotId; }
    public Role getProfessionalType() { return professionalType; }
    public BookingStatus getStatus() { return status; }
    public LocalDateTime getScheduledStart() { return scheduledStart; }
    public LocalDateTime getScheduledEnd() { return scheduledEnd; }
    public String getProviderNote() { return providerNote; }
    public LocalDateTime getNoteLockedAt() { return noteLockedAt; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public LocalDateTime getNoShowAt() { return noShowAt; }
    public LocalDateTime getCancelledAt() { return cancelledAt; }
    public String getCancelReason() { return cancelReason; }
    public Long getRescheduledFromBookingId() { return rescheduledFromBookingId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
