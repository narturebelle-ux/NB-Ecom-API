package com.narturebelle.repository;

import com.narturebelle.model.AccountDeletionRequest;
import com.narturebelle.model.DeletionRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AccountDeletionRequestRepository extends JpaRepository<AccountDeletionRequest, Long> {
    Optional<AccountDeletionRequest> findTopByUserIdAndStatusNotOrderByRequestedAtDesc(Long userId, DeletionRequestStatus excluded);
    boolean existsByUserIdAndStatusIn(Long userId, Collection<DeletionRequestStatus> statuses);
    List<AccountDeletionRequest> findByUserIdOrderByRequestedAtDesc(Long userId);
    List<AccountDeletionRequest> findAllByOrderByRequestedAtDesc();
}
