package com.narturebelle.repository;

import com.narturebelle.model.Booking;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long> {
    List<Booking> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<Booking> findByProviderIdOrderByCreatedAtDesc(Long providerId);
}
