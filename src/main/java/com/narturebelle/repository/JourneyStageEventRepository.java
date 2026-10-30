package com.narturebelle.repository;

import com.narturebelle.model.JourneyStageEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JourneyStageEventRepository extends JpaRepository<JourneyStageEvent, Long> {
    List<JourneyStageEvent> findByJourneyIdOrderByOccurredAtAsc(Long journeyId);
}
