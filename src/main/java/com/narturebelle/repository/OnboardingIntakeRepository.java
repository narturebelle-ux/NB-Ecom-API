package com.narturebelle.repository;

import com.narturebelle.model.OnboardingIntake;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OnboardingIntakeRepository extends JpaRepository<OnboardingIntake, Long> {
    Optional<OnboardingIntake> findByUserId(Long userId);
}
