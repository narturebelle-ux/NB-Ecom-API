package com.narturebelle.repository;

import com.narturebelle.model.SessionFeedback;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SessionFeedbackRepository extends JpaRepository<SessionFeedback, Long> {
    Optional<SessionFeedback> findByBookingId(Long bookingId);
    boolean existsByBookingId(Long bookingId);
}
