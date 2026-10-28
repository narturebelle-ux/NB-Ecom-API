package com.narturebelle.repository;

import com.narturebelle.model.Journey;
import com.narturebelle.model.JourneyStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface JourneyRepository extends JpaRepository<Journey, Long> {

    List<Journey> findByUserIdOrderByStartedAtDesc(Long userId);

    Optional<Journey> findByUserIdAndStatusNot(Long userId, JourneyStatus status);

    boolean existsByUserIdAndStatusNot(Long userId, JourneyStatus status);
}
