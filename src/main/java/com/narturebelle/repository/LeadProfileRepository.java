package com.narturebelle.repository;

import com.narturebelle.model.LeadProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LeadProfileRepository extends JpaRepository<LeadProfile, Long> {
    Optional<LeadProfile> findByPhone(String phone);
    Optional<LeadProfile> findByEmail(String email);
}
