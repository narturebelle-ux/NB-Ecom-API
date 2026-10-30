package com.narturebelle.repository;

import com.narturebelle.model.OnboardingCallbackRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OnboardingCallbackRequestRepository extends JpaRepository<OnboardingCallbackRequest, Long> {
    List<OnboardingCallbackRequest> findAllByOrderByCreatedAtDesc();
    Optional<OnboardingCallbackRequest> findTopByUserIdOrderByCreatedAtDesc(Long userId);
    List<OnboardingCallbackRequest> findByUserIdOrderByCreatedAtDesc(Long userId);
}
