package com.narturebelle.controller;

import com.narturebelle.dto.ProductHealthRuleResponse;
import com.narturebelle.dto.RejectRuleRequest;
import com.narturebelle.model.RuleReviewStatus;
import com.narturebelle.security.AuthorizationService;
import com.narturebelle.service.ProductHealthRuleService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Review queue for product health rules. Every endpoint checks AuthorizationService.requireRuleReviewer()
 * (MEDICAL_REVIEWER, or a verified gynecologist / nutritionist / registered nurse) -> 403 otherwise. */
@RestController
@RequestMapping("/api/v1/review/product-health-rules")
public class ProductHealthRuleReviewController {

    private final ProductHealthRuleService ruleService;
    private final AuthorizationService authorizationService;

    public ProductHealthRuleReviewController(ProductHealthRuleService ruleService, AuthorizationService authorizationService) {
        this.ruleService = ruleService;
        this.authorizationService = authorizationService;
    }

    /** Defaults to the pending queue, oldest first; ?status=APPROVED|REJECTED for the history. */
    @GetMapping
    public ResponseEntity<List<ProductHealthRuleResponse>> queue(
            @RequestParam(defaultValue = "PENDING_CLINICAL_REVIEW") RuleReviewStatus status) {
        authorizationService.requireRuleReviewer();
        return ResponseEntity.ok(ruleService.list(status).stream().map(ProductHealthRuleResponse::from).toList());
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<ProductHealthRuleResponse> approve(@PathVariable Long id) {
        var reviewer = authorizationService.requireRuleReviewer();
        return ResponseEntity.ok(ProductHealthRuleResponse.from(ruleService.approve(id, reviewer)));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<ProductHealthRuleResponse> reject(@PathVariable Long id, @Valid @RequestBody RejectRuleRequest req) {
        var reviewer = authorizationService.requireRuleReviewer();
        return ResponseEntity.ok(ProductHealthRuleResponse.from(ruleService.reject(id, reviewer, req.getReason())));
    }
}
