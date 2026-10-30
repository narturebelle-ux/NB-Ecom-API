package com.narturebelle.repository;

import com.narturebelle.model.Booking;
import com.narturebelle.model.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long> {
    List<Booking> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<Booking> findByProviderIdOrderByCreatedAtDesc(Long providerId);
    List<Booking> findByStatusAndScheduledStartBetween(BookingStatus status, LocalDateTime from, LocalDateTime to);
}
