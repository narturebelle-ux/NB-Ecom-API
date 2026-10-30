package com.narturebelle.repository;

import com.narturebelle.model.OnboardingCompletion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OnboardingCompletionRepository extends JpaRepository<OnboardingCompletion, Long> {
    Optional<OnboardingCompletion> findByUserId(Long userId);
}
