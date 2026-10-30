package com.narturebelle.repository;

import com.narturebelle.model.ReferralCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReferralCodeRepository extends JpaRepository<ReferralCode, Long> {
    Optional<ReferralCode> findByCode(String code);
}
