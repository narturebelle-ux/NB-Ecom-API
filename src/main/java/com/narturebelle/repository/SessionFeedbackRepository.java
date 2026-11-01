package com.narturebelle.repository;

import com.narturebelle.model.SessionFeedback;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SessionFeedbackRepository extends JpaRepository<SessionFeedback, Long> {
    Optional<SessionFeedback> findByBookingId(Long bookingId);
    boolean existsByBookingId(Long bookingId);

    /** Feedback on a professional's sessions, newest first. */
    @org.springframework.data.jpa.repository.Query("select f from SessionFeedback f, Booking b"
            + " where b.id = f.bookingId and b.providerId = :providerId order by f.createdAt desc")
    java.util.List<SessionFeedback> findForProvider(@org.springframework.data.repository.query.Param("providerId") Long providerId,
                                                    org.springframework.data.domain.Pageable page);
}
