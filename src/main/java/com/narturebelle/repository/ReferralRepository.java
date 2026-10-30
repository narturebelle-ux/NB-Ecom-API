package com.narturebelle.repository;

import com.narturebelle.model.Referral;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReferralRepository extends JpaRepository<Referral, Long> {
    Optional<Referral> findByReferredUserId(Long referredUserId);
    List<Referral> findByReferrerUserIdOrderByCreatedAtDesc(Long referrerUserId);
    long countByReferrerUserIdAndQualifiedAtIsNotNull(Long referrerUserId);
}
