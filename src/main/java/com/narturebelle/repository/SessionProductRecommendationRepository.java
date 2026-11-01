package com.narturebelle.repository;

import com.narturebelle.model.SessionProductRecommendation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SessionProductRecommendationRepository extends JpaRepository<SessionProductRecommendation, Long> {
    List<SessionProductRecommendation> findTop20ByProviderIdOrderByCreatedAtDesc(Long providerId);
    List<SessionProductRecommendation> findByBookingIdOrderByIdAsc(Long bookingId);
    List<SessionProductRecommendation> findByMotherUserId(Long motherUserId);
    void deleteByBookingId(Long bookingId);
}
