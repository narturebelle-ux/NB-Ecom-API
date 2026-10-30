package com.narturebelle.repository;

import com.narturebelle.model.PayoutRate;
import com.narturebelle.model.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PayoutRateRepository extends JpaRepository<PayoutRate, Long> {
    Optional<PayoutRate> findByProfessionalType(Role professionalType);
}
