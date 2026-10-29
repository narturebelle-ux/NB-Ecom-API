package com.narturebelle.repository;

import com.narturebelle.model.Entitlement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EntitlementRepository extends JpaRepository<Entitlement, Long> {
    List<Entitlement> findByUserIdOrderByCreatedAtDesc(Long userId);
    boolean existsByOrderId(Long orderId);
}
