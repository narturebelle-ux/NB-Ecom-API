package com.narturebelle.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** Append-only history of provider-note amendments after a booking is completed — same shape as JourneyStageEvent. */
@Entity
@Table(name = "booking_note_amendments")
public class BookingNoteAmendment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long bookingId;

    @Column(columnDefinition = "text")
    private String previousNote;

    @Column(columnDefinition = "text", nullable = false)
    private String newNote;

    @Column(columnDefinition = "text")
    private String reason;

    @Column(nullable = false)
    private Long amendedByUserId;

    @Column(nullable = false)
    private LocalDateTime amendedAt;

    @PrePersist
    protected void onCreate() {
        if (this.amendedAt == null) {
            this.amendedAt = LocalDateTime.now();
        }
    }

    public BookingNoteAmendment() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getBookingId() { return bookingId; }
    public void setBookingId(Long bookingId) { this.bookingId = bookingId; }

    public String getPreviousNote() { return previousNote; }
    public void setPreviousNote(String previousNote) { this.previousNote = previousNote; }

    public String getNewNote() { return newNote; }
    public void setNewNote(String newNote) { this.newNote = newNote; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public Long getAmendedByUserId() { return amendedByUserId; }
    public void setAmendedByUserId(Long amendedByUserId) { this.amendedByUserId = amendedByUserId; }

    public LocalDateTime getAmendedAt() { return amendedAt; }
    public void setAmendedAt(LocalDateTime amendedAt) { this.amendedAt = amendedAt; }
}
