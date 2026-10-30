package com.narturebelle.service;

import com.narturebelle.exception.NoEntitlementAvailableException;
import com.narturebelle.model.Entitlement;
import com.narturebelle.model.Order;
import com.narturebelle.model.PlanEntitlement;
import com.narturebelle.model.PlanVersion;
import com.narturebelle.model.Role;
import com.narturebelle.repository.EntitlementRepository;
import com.narturebelle.repository.PlanVersionRepository;
import com.narturebelle.security.AuditService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Component
public class EntitlementService {

    private final EntitlementRepository entitlementRepository;
    private final PlanVersionRepository planVersionRepository;
    private final PlanService planService;
    private final AuditService auditService;

    public EntitlementService(EntitlementRepository entitlementRepository, PlanVersionRepository planVersionRepository,
                               PlanService planService, AuditService auditService) {
        this.entitlementRepository = entitlementRepository;
        this.planVersionRepository = planVersionRepository;
        this.planService = planService;
        this.auditService = auditService;
    }

    /** Idempotent: a repeated call for an order that already has entitlements is a no-op. */
    public void grantForOrder(Order order) {
        if (entitlementRepository.existsByOrderId(order.getId())) {
            return;
        }

        PlanVersion planVersion = planVersionRepository.findById(order.getPlanVersionId())
                .orElseThrow(() -> new IllegalStateException("Order references a missing PlanVersion: " + order.getPlanVersionId()));
        List<PlanEntitlement> allocations = planService.allocationsFor(planVersion.getId());
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(planVersion.getValidityDays());

        for (PlanEntitlement allocation : allocations) {
            Entitlement entitlement = new Entitlement();
            entitlement.setUserId(order.getUserId());
            entitlement.setOrderId(order.getId());
            entitlement.setProfessionalType(allocation.getProfessionalType());
            entitlement.setTotalSessions(allocation.getSessionCount());
            entitlement.setUsedSessions(0);
            entitlement.setExpiresAt(expiresAt);
            try {
                entitlementRepository.save(entitlement);
            } catch (DataIntegrityViolationException alreadyGranted) {
                // Lost a race to a concurrent webhook/mock delivery for the same order+professionalType.
            }
        }

        auditService.record("ENTITLEMENTS_GRANTED", "Order", String.valueOf(order.getId()), null,
                Map.of("planVersionId", planVersion.getId(), "allocationCount", allocations.size()));
    }

    public List<Entitlement> walletForUser(Long userId) {
        return entitlementRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public boolean hasAvailableSession(Long userId, Role professionalType) {
        return entitlementRepository.existsConsumable(userId, professionalType, LocalDateTime.now());
    }

    /**
     * Consumes exactly one session, FIFO by expiresAt, with a pessimistic write lock on the
     * candidate row — this is a "find-then-increment" pattern, not a "client has it open and
     * submits an edit" pattern, so a lock is the right tool here rather than optimistic (@Version)
     * retry logic. Only ever called from session completion, never at booking time.
     */
    @Transactional
    public Entitlement consumeOneSession(Long userId, Role professionalType) {
        List<Entitlement> candidates = entitlementRepository.findConsumableForUpdate(userId, professionalType, LocalDateTime.now());
        if (candidates.isEmpty()) {
            throw new NoEntitlementAvailableException(
                    "No available " + professionalType + " session for user " + userId);
        }
        Entitlement entitlement = candidates.get(0);
        entitlement.setUsedSessions(entitlement.getUsedSessions() + 1);
        Entitlement saved = entitlementRepository.save(entitlement);

        auditService.record("ENTITLEMENT_CONSUMED", "Entitlement", String.valueOf(saved.getId()), null,
                Map.of("professionalType", professionalType.name()));

        return saved;
    }
}
