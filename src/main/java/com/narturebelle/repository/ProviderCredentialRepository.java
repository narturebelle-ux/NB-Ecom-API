package com.narturebelle.repository;

import com.narturebelle.model.ProviderCredential;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProviderCredentialRepository extends JpaRepository<ProviderCredential, Long> {
    List<ProviderCredential> findByProviderId(Long providerId);
}
