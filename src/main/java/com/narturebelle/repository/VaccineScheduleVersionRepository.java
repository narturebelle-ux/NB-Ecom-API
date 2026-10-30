package com.narturebelle.repository;

import com.narturebelle.model.VaccineScheduleVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VaccineScheduleVersionRepository extends JpaRepository<VaccineScheduleVersion, Long> {
    Optional<VaccineScheduleVersion> findFirstByActiveTrueOrderByVersionDesc();
}
