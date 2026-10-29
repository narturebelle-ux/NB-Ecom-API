package com.narturebelle.repository;

import com.narturebelle.model.OnboardingCallbackRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OnboardingCallbackRequestRepository extends JpaRepository<OnboardingCallbackRequest, Long> {
    List<OnboardingCallbackRequest> findAllByOrderByCreatedAtDesc();
}
