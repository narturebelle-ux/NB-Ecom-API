package com.narturebelle.repository;

import com.narturebelle.model.Payout;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PayoutRepository extends JpaRepository<Payout, Long> {
    List<Payout> findByProviderId(Long providerId);
    Optional<Payout> findByBookingId(Long bookingId);
}
