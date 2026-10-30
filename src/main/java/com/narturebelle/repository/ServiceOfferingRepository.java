package com.narturebelle.repository;

import com.narturebelle.model.ServiceOffering;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ServiceOfferingRepository extends JpaRepository<ServiceOffering, Long> {
    List<ServiceOffering> findByProviderId(Long providerId);
    Optional<ServiceOffering> findByIdAndProviderId(Long id, Long providerId);
}
