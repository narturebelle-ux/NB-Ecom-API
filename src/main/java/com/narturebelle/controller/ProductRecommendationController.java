package com.narturebelle.controller;

import com.narturebelle.dto.ProductRecommendationsResponse;
import com.narturebelle.exception.UserProfileNotFoundException;
import com.narturebelle.security.AuthorizationService;
import com.narturebelle.security.FirebaseUserPrincipal;
import com.narturebelle.security.RequireConsent;
import com.narturebelle.service.ProductRecommendationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

/** Personalised product recommendations (Phase 6). The public /api/v1/products catalog is unchanged. */
@RestController
@RequestMapping("/api/v1/me/product-recommendations")
public class ProductRecommendationController {

    private final ProductRecommendationService productRecommendationService;
    private final AuthorizationService authorizationService;

    public ProductRecommendationController(ProductRecommendationService productRecommendationService,
                                           AuthorizationService authorizationService) {
        this.productRecommendationService = productRecommendationService;
        this.authorizationService = authorizationService;
    }

    @RequireConsent
    @GetMapping
    public ResponseEntity<ProductRecommendationsResponse> recommend(@RequestParam(required = false) String category,
                                                                    @RequestParam(required = false) BigDecimal maxPrice,
                                                                    @RequestParam(defaultValue = "false") boolean includeDismissed) {
        Long userId = requireUserId(authorizationService.currentPrincipal());
        return ResponseEntity.ok(productRecommendationService.recommend(userId, category, maxPrice, includeDismissed));
    }

    @RequireConsent
    @PostMapping("/{productId}/dismiss")
    public ResponseEntity<Void> dismiss(@PathVariable Long productId) {
        Long userId = requireUserId(authorizationService.currentPrincipal());
        productRecommendationService.dismiss(userId, productId);
        return ResponseEntity.noContent().build();
    }

    @RequireConsent
    @DeleteMapping("/{productId}/dismiss")
    public ResponseEntity<Void> undismiss(@PathVariable Long productId) {
        Long userId = requireUserId(authorizationService.currentPrincipal());
        productRecommendationService.undismiss(userId, productId);
        return ResponseEntity.noContent().build();
    }

    private Long requireUserId(FirebaseUserPrincipal principal) {
        if (principal.userId() == null) {
            throw new UserProfileNotFoundException("Call POST /api/v1/auth/firebase first to create your profile.");
        }
        return principal.userId();
    }
}
