package com.narturebelle.service;

import com.narturebelle.model.Plan;
import com.narturebelle.model.PlanCode;
import com.narturebelle.model.PlanEntitlement;
import com.narturebelle.model.PlanVersion;
import com.narturebelle.repository.PlanEntitlementRepository;
import com.narturebelle.repository.PlanRepository;
import com.narturebelle.repository.PlanVersionRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class PlanService {

    private final PlanRepository planRepository;
    private final PlanVersionRepository planVersionRepository;
    private final PlanEntitlementRepository planEntitlementRepository;

    public PlanService(PlanRepository planRepository, PlanVersionRepository planVersionRepository,
                        PlanEntitlementRepository planEntitlementRepository) {
        this.planRepository = planRepository;
        this.planVersionRepository = planVersionRepository;
        this.planEntitlementRepository = planEntitlementRepository;
    }

    public List<Plan> listAllOrdered() {
        return planRepository.findAll().stream()
                .sorted(java.util.Comparator.comparingInt(Plan::getDisplayOrder))
                .toList();
    }

    public Optional<Plan> findPlan(Long planId) {
        return planRepository.findById(planId);
    }

    public Optional<PlanVersion> findActiveVersion(Long planId) {
        return planVersionRepository.findByPlanIdAndActiveTrue(planId);
    }

    public Optional<PlanVersion> findVersionById(Long planVersionId) {
        return planVersionRepository.findById(planVersionId);
    }

    /** Resolves the PlanCode an Order's planVersionId belongs to, regardless of whether that version is still active. */
    public Optional<PlanCode> resolvePlanCode(Long planVersionId) {
        return findVersionById(planVersionId)
                .flatMap(version -> findPlan(version.getPlanId()))
                .map(Plan::getCode);
    }

    public List<PlanEntitlement> allocationsFor(Long planVersionId) {
        return planEntitlementRepository.findByPlanVersionId(planVersionId);
    }
}
