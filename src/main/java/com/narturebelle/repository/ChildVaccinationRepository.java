package com.narturebelle.repository;

import com.narturebelle.model.ChildVaccination;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChildVaccinationRepository extends JpaRepository<ChildVaccination, Long> {
    List<ChildVaccination> findByChildId(Long childId);
    Optional<ChildVaccination> findByChildIdAndScheduleItemId(Long childId, Long scheduleItemId);
}
