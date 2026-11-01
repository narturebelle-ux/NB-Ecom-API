package com.narturebelle.providers.repository;

import com.narturebelle.providers.model.ApplicationStatus;
import com.narturebelle.providers.model.ProviderApplication;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProviderApplicationRepository extends JpaRepository<ProviderApplication, Long> {

    Optional<ProviderApplication> findFirstByUserIdOrderByCreatedAtDesc(Long userId);

    boolean existsByUserIdAndFraudBlockedTrue(Long userId);

    Optional<ProviderApplication> findByProviderId(Long providerId);

    List<ProviderApplication> findByStatusInOrderBySubmittedAtAsc(Collection<ApplicationStatus> statuses);

    List<ProviderApplication> findByStatus(ApplicationStatus status);

    /** Another application holding this registration number, other than ones closed without a fraud finding. */
    @Query("select count(a) > 0 from ProviderApplication a where a.registrationNumber = :number and a.id <> :id"
            + " and (a.fraudBlocked = true or a.status not in (com.narturebelle.providers.model.ApplicationStatus.REJECTED,"
            + " com.narturebelle.providers.model.ApplicationStatus.WITHDRAWN, com.narturebelle.providers.model.ApplicationStatus.CLOSED))")
    boolean registrationHeldElsewhere(@Param("number") String number, @Param("id") Long id);
}
