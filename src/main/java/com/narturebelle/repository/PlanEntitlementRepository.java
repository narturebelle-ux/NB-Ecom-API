package com.narturebelle.repository;

import com.narturebelle.model.PlanEntitlement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlanEntitlementRepository extends JpaRepository<PlanEntitlement, Long> {
    List<PlanEntitlement> findByPlanVersionId(Long planVersionId);
}
