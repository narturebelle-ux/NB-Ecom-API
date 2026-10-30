package com.narturebelle.dto;

import com.narturebelle.model.SessionFeedback;

import java.time.LocalDateTime;

public class SessionFeedbackResponse {

    private Long id;
    private Long bookingId;
    private Integer rating;
    private String comment;
    private LocalDateTime createdAt;

    public static SessionFeedbackResponse from(SessionFeedback feedback) {
        SessionFeedbackResponse dto = new SessionFeedbackResponse();
        dto.id = feedback.getId();
        dto.bookingId = feedback.getBookingId();
        dto.rating = feedback.getRating();
        dto.comment = feedback.getComment();
        dto.createdAt = feedback.getCreatedAt();
        return dto;
    }

    public Long getId() { return id; }
    public Long getBookingId() { return bookingId; }
    public Integer getRating() { return rating; }
    public String getComment() { return comment; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
