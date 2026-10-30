package com.narturebelle.controller;

import com.narturebelle.dto.EntitlementResponse;
import com.narturebelle.exception.UserProfileNotFoundException;
import com.narturebelle.model.PlanCode;
import com.narturebelle.security.AuthorizationService;
import com.narturebelle.security.FirebaseUserPrincipal;
import com.narturebelle.service.EntitlementService;
import com.narturebelle.service.OrderService;
import com.narturebelle.service.PlanService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Authenticated only (no @RequireVerified) — viewing the wallet is lower-stakes than paying, same as GET /me vs PATCH /me. */
@RestController
@RequestMapping("/api/v1/entitlements")
public class EntitlementController {

    private final EntitlementService entitlementService;
    private final OrderService orderService;
    private final PlanService planService;
    private final AuthorizationService authorizationService;

    public EntitlementController(EntitlementService entitlementService, OrderService orderService,
                                  PlanService planService, AuthorizationService authorizationService) {
        this.entitlementService = entitlementService;
        this.orderService = orderService;
        this.planService = planService;
        this.authorizationService = authorizationService;
    }

    @GetMapping
    public ResponseEntity<List<EntitlementResponse>> list() {
        FirebaseUserPrincipal principal = authorizationService.currentPrincipal();
        if (principal.userId() == null) {
            throw new UserProfileNotFoundException("Call POST /api/v1/auth/firebase first to create your profile.");
        }
        List<EntitlementResponse> wallet = entitlementService.walletForUser(principal.userId()).stream()
                .map(e -> EntitlementResponse.from(e, resolvePlanCode(e.getOrderId())))
                .toList();
        return ResponseEntity.ok(wallet);
    }

    private PlanCode resolvePlanCode(Long orderId) {
        return orderService.find(orderId)
                .flatMap(order -> planService.resolvePlanCode(order.getPlanVersionId()))
                .orElse(null);
    }
}
