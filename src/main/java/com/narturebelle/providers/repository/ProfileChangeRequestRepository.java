package com.narturebelle.providers.repository;

import com.narturebelle.providers.model.ProfileChangeRequest;
import com.narturebelle.providers.model.ProfileChangeStatus;
import com.narturebelle.providers.model.ProfileChangeType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProfileChangeRequestRepository extends JpaRepository<ProfileChangeRequest, Long> {

    List<ProfileChangeRequest> findByApplicationIdOrderByCreatedAtDesc(Long applicationId);

    List<ProfileChangeRequest> findByStatusOrderByCreatedAtAsc(ProfileChangeStatus status);

    boolean existsByApplicationIdAndChangeTypeAndStatus(Long applicationId, ProfileChangeType changeType, ProfileChangeStatus status);
}
