package com.narturebelle.controller;

import com.narturebelle.dto.PlanQuoteResponse;
import com.narturebelle.dto.PlanResponse;
import com.narturebelle.exception.UserProfileNotFoundException;
import com.narturebelle.model.Plan;
import com.narturebelle.model.PlanVersion;
import com.narturebelle.security.AuthorizationService;
import com.narturebelle.security.FirebaseUserPrincipal;
import com.narturebelle.security.RequireConsent;
import com.narturebelle.service.PlanPricing;
import com.narturebelle.service.PlanService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;

/** Plans priced for the signed-in user's remaining months -- exactly what POST /orders will charge her. */
@RestController
@RequestMapping("/api/v1/me/plans/quote")
public class PlanQuoteController {

    private final PlanService planService;
    private final PlanPricing planPricing;
    private final AuthorizationService authorizationService;

    public PlanQuoteController(PlanService planService, PlanPricing planPricing, AuthorizationService authorizationService) {
        this.planService = planService;
        this.planPricing = planPricing;
        this.authorizationService = authorizationService;
    }

    @RequireConsent
    @GetMapping
    public ResponseEntity<PlanQuoteResponse> quote() {
        Long userId = requireUserId(authorizationService.currentPrincipal());
        PlanPricing.Duration duration = planPricing.durationFor(userId);
        List<PlanResponse> plans = planService.listAllOrdered().stream()
                .map(plan -> priced(plan, duration.months()))
                .filter(Objects::nonNull)
                .toList();
        return ResponseEntity.ok(new PlanQuoteResponse(duration.months(), duration.fromDueDate() ? "DUE_DATE" : "FULL_PLAN", plans));
    }

    private PlanResponse priced(Plan plan, int months) {
        PlanVersion version = planService.findActiveVersion(plan.getId()).orElse(null);
        if (version == null) return null;
        return PlanResponse.forMonths(plan.getId(), plan.getCode(), plan.getName(), version,
                planService.allocationsFor(version.getId()), months);
    }

    private Long requireUserId(FirebaseUserPrincipal principal) {
        if (principal.userId() == null) {
            throw new UserProfileNotFoundException("Call POST /api/v1/auth/firebase first to create your profile.");
        }
        return principal.userId();
    }
}
