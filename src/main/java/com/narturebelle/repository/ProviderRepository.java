package com.narturebelle.repository;

import com.narturebelle.model.Provider;
import com.narturebelle.model.ProviderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProviderRepository extends JpaRepository<Provider, Long> {
    Optional<Provider> findByUserId(Long userId);
    List<Provider> findByStatusIn(List<ProviderStatus> statuses);
}
