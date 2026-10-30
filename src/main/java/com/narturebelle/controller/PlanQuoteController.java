package com.narturebelle.controller;

import com.narturebelle.dto.PlanQuoteResponse;
import com.narturebelle.dto.PlanResponse;
import com.narturebelle.model.Plan;
import com.narturebelle.model.PlanVersion;
import com.narturebelle.security.RequireConsent;
import com.narturebelle.service.PlanPricing;
import com.narturebelle.service.PlanService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;

/** The plans and what POST /orders will charge. Prices are constant, so this is the same for everyone;
 * kept so existing clients keep working. basis is always FULL_PLAN. */
@RestController
@RequestMapping("/api/v1/me/plans/quote")
public class PlanQuoteController {

    private final PlanService planService;

    public PlanQuoteController(PlanService planService) {
        this.planService = planService;
    }

    @RequireConsent
    @GetMapping
    public ResponseEntity<PlanQuoteResponse> quote() {
        List<PlanResponse> plans = planService.listAllOrdered().stream()
                .map(this::priced)
                .filter(Objects::nonNull)
                .toList();
        return ResponseEntity.ok(new PlanQuoteResponse(PlanPricing.FULL_MONTHS, "FULL_PLAN", plans));
    }

    private PlanResponse priced(Plan plan) {
        PlanVersion version = planService.findActiveVersion(plan.getId()).orElse(null);
        if (version == null) return null;
        return PlanResponse.from(plan.getId(), plan.getCode(), plan.getName(), version, planService.allocationsFor(version.getId()));
    }
}
