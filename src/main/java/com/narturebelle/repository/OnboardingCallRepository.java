package com.narturebelle.repository;

import com.narturebelle.model.OnboardingCall;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OnboardingCallRepository extends JpaRepository<OnboardingCall, Long> {
    Optional<OnboardingCall> findByUserId(Long userId);
}
