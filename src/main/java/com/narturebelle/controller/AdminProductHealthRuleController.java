package com.narturebelle.controller;

import com.narturebelle.dto.CreateProductHealthRuleRequest;
import com.narturebelle.dto.ProductHealthRuleResponse;
import com.narturebelle.model.RuleReviewStatus;
import com.narturebelle.security.AuthorizationService;
import com.narturebelle.service.ProductHealthRuleService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** /api/v1/admin/** is already role-gated by SecurityConfig (any *_ADMIN role). Admins propose and delete
 * rules; reviewing them happens under /api/v1/review (ProductHealthRuleReviewController). */
@RestController
@RequestMapping("/api/v1/admin/product-health-rules")
public class AdminProductHealthRuleController {

    private final ProductHealthRuleService ruleService;
    private final AuthorizationService authorizationService;

    public AdminProductHealthRuleController(ProductHealthRuleService ruleService, AuthorizationService authorizationService) {
        this.ruleService = ruleService;
        this.authorizationService = authorizationService;
    }

    @GetMapping
    public ResponseEntity<List<ProductHealthRuleResponse>> list(@RequestParam(required = false) RuleReviewStatus status) {
        return ResponseEntity.ok(ruleService.list(status).stream().map(ProductHealthRuleResponse::from).toList());
    }

    @PostMapping
    public ResponseEntity<ProductHealthRuleResponse> create(@Valid @RequestBody CreateProductHealthRuleRequest req) {
        Long creator = authorizationService.currentPrincipal().userId();
        return ResponseEntity.ok(ProductHealthRuleResponse.from(ruleService.create(creator, req)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        ruleService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
