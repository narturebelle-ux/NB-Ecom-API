package com.narturebelle.repository;

import com.narturebelle.model.DoctorSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DoctorSessionRepository extends JpaRepository<DoctorSession, Long> {
    List<DoctorSession> findByStatus(String status);
}
