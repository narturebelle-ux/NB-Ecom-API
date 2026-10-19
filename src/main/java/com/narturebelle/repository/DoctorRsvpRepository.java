package com.narturebelle.repository;

import com.narturebelle.model.DoctorRsvp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DoctorRsvpRepository extends JpaRepository<DoctorRsvp, Long> {
    List<DoctorRsvp> findBySessionId(Long sessionId);
}
