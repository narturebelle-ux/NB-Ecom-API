package com.narturebelle.repository;

import com.narturebelle.model.HospitalReferral;
import com.narturebelle.model.ReferralStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface HospitalReferralRepository extends JpaRepository<HospitalReferral, Long> {
    List<HospitalReferral> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<HospitalReferral> findAllByOrderByCreatedAtDesc();
    Optional<HospitalReferral> findFirstByUserIdAndHospitalIdAndStatus(Long userId, Long hospitalId, ReferralStatus status);
    boolean existsByCode(String code);
}
