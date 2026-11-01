package com.narturebelle.providers.repository;

import com.narturebelle.providers.model.RequestedChange;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface RequestedChangeRepository extends JpaRepository<RequestedChange, Long> {

    List<RequestedChange> findByApplicationId(Long applicationId);

    @Transactional
    void deleteByApplicationId(Long applicationId);
}
