package com.narturebelle.repository;

import com.narturebelle.model.BookingNoteAmendment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookingNoteAmendmentRepository extends JpaRepository<BookingNoteAmendment, Long> {
    List<BookingNoteAmendment> findByBookingIdOrderByAmendedAtDesc(Long bookingId);
}
