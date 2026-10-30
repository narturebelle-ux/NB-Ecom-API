package com.narturebelle.repository;

import com.narturebelle.model.Coordinator;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CoordinatorRepository extends JpaRepository<Coordinator, Long> {
    List<Coordinator> findAllByOrderByDisplayNameAsc();
    Optional<Coordinator> findFirstByDisplayName(String displayName);
}
