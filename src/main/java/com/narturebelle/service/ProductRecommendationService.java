package com.narturebelle.service;

import com.narturebelle.dto.ProductRecommendationResponse;
import com.narturebelle.dto.ProductRecommendationsResponse;
import com.narturebelle.exception.ProductNotFoundException;
import com.narturebelle.model.Journey;
import com.narturebelle.model.JourneyStatus;
import com.narturebelle.model.NecessityTier;
import com.narturebelle.model.PregnancyStage;
import com.narturebelle.model.Product;
import com.narturebelle.model.ProductHealthRule;
import com.narturebelle.model.RuleEffect;
import com.narturebelle.model.ProductRecommendationDismissal;
import com.narturebelle.repository.JourneyRepository;
import com.narturebelle.repository.ProductRecommendationDismissalRepository;
import com.narturebelle.repository.ProductRepository;
import com.narturebelle.security.AuditService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Product recommendations (Phase 6) over the existing Product catalog. Base reasons are non-clinical
 * (stage, chosen category, budget) -- never Product.clinicalRationale, which has had no clinical review.
 * Health details shape results only through clinically APPROVED ProductHealthRules, and only for users
 * with the HEALTH_DATA consent. SKIP-tier products are never recommended.
 */
@Service
public class ProductRecommendationService {

    private final ProductRepository productRepository;
    private final ProductRecommendationDismissalRepository dismissalRepository;
    private final JourneyRepository journeyRepository;
    private final AuditService auditService;
    private final String commissionDisclosure;
    private final HealthProfileService healthProfileService;
    private final ProductHealthRuleService productHealthRuleService;

    public ProductRecommendationService(ProductRepository productRepository,
                                        ProductRecommendationDismissalRepository dismissalRepository,
                                        JourneyRepository journeyRepository,
                                        AuditService auditService,
                                        HealthProfileService healthProfileService,
                                        ProductHealthRuleService productHealthRuleService,
                                        @Value("${narturebelle.recommendations.commission-disclosure:"
                                                + "NartureBelle may earn a commission if you buy through these recommendations. "
                                                + "It never affects what we recommend.}") String commissionDisclosure) {
        this.productRepository = productRepository;
        this.dismissalRepository = dismissalRepository;
        this.journeyRepository = journeyRepository;
        this.auditService = auditService;
        this.commissionDisclosure = commissionDisclosure;
        this.healthProfileService = healthProfileService;
        this.productHealthRuleService = productHealthRuleService;
    }

    public ProductRecommendationsResponse recommend(Long userId, String category, BigDecimal maxPrice, boolean includeDismissed) {
        PregnancyStage stage = journeyRepository.findByUserIdAndStatusNot(userId, JourneyStatus.CLOSED)
                .map(this::stageFor)
                .orElse(null);
        if (stage == null) {
            return ProductRecommendationsResponse.of(null, commissionDisclosure, List.of());
        }

        Set<Long> dismissed = dismissalRepository.findByUserId(userId).stream()
                .map(ProductRecommendationDismissal::getProductId)
                .collect(Collectors.toSet());

        // Health-aware rules: only with HEALTH_DATA consent + a health profile, and only APPROVED rules.
        List<ProductHealthRule> rules = healthProfileService.forRecommendations(userId)
                .map(profile -> productHealthRuleService.approvedFor(profile.getRiskFactors()))
                .orElse(List.of());
        Set<String> avoidTags = rules.stream().filter(r -> r.getEffect() == RuleEffect.AVOID)
                .map(ProductHealthRule::getTag).collect(Collectors.toSet());
        List<ProductHealthRule> preferRules = rules.stream().filter(r -> r.getEffect() == RuleEffect.PREFER).toList();

        List<ProductRecommendationResponse> items = productRepository.findByStage(stage).stream()
                .filter(p -> p.getTier() != NecessityTier.SKIP)
                .filter(p -> category == null || category.isBlank() || category.equalsIgnoreCase(p.getCategory()))
                .filter(p -> maxPrice == null || p.getPrice().compareTo(maxPrice) <= 0)
                .filter(p -> includeDismissed || !dismissed.contains(p.getId()))
                // AVOID wins over PREFER: a product carrying any avoided tag is never recommended.
                .filter(p -> p.getTags().stream().noneMatch(avoidTags::contains))
                // Health-preferred first, then essentials, then price -- the tier itself is never exposed.
                .sorted(Comparator.comparing((Product p) -> preferReasons(p, preferRules).isEmpty() ? 1 : 0)
                        .thenComparing(p -> p.getTier() == NecessityTier.MANDATORY ? 0 : 1)
                        .thenComparing(Product::getPrice))
                .map(p -> {
                    List<String> why = new ArrayList<>(reasons(stage, category, maxPrice));
                    why.addAll(preferReasons(p, preferRules));
                    return ProductRecommendationResponse.from(p, why, dismissed.contains(p.getId()));
                })
                .toList();
        return ProductRecommendationsResponse.of(stage, commissionDisclosure, items);
    }

    /** The reviewed reason text of each PREFER rule whose tag this product carries, de-duplicated. */
    private static List<String> preferReasons(Product product, List<ProductHealthRule> preferRules) {
        return preferRules.stream()
                .filter(rule -> product.getTags().contains(rule.getTag()))
                .map(ProductHealthRule::getReason)
                .distinct()
                .toList();
    }

    /** Idempotent: dismissing twice keeps one row. */
    @Transactional
    public void dismiss(Long userId, Long productId) {
        requireProduct(productId);
        if (dismissalRepository.findByUserIdAndProductId(userId, productId).isPresent()) {
            return;
        }
        ProductRecommendationDismissal dismissal = new ProductRecommendationDismissal();
        dismissal.setUserId(userId);
        dismissal.setProductId(productId);
        dismissalRepository.save(dismissal);
        auditService.record("PRODUCT_RECOMMENDATION_DISMISSED", "PRODUCT", String.valueOf(productId),
                "User dismissed a product recommendation");
    }

    /** Idempotent: un-dismissing a product that wasn't dismissed is a no-op. */
    @Transactional
    public void undismiss(Long userId, Long productId) {
        requireProduct(productId);
        dismissalRepository.findByUserIdAndProductId(userId, productId).ifPresent(dismissal -> {
            dismissalRepository.delete(dismissal);
            auditService.record("PRODUCT_RECOMMENDATION_RESTORED", "PRODUCT", String.valueOf(productId),
                    "User restored a dismissed product recommendation");
        });
    }

    private void requireProduct(Long productId) {
        if (!productRepository.existsById(productId)) {
            throw new ProductNotFoundException("Product " + productId + " not found.");
        }
    }

    /** Trimester 1/2/3 -> MONTH_3/6/9; after birth -> BABY_CARE. Null without an EDD to place her in a trimester. */
    private PregnancyStage stageFor(Journey journey) {
        return switch (journey.getStatus()) {
            case PREGNANCY -> {
                Integer trimester = GestationalMath.trimester(journey.getStatus(), journey.getExpectedDeliveryDate());
                yield trimester == null ? null
                        : trimester == 1 ? PregnancyStage.MONTH_3
                        : trimester == 2 ? PregnancyStage.MONTH_6
                        : PregnancyStage.MONTH_9;
            }
            case PRE_DELIVERY -> PregnancyStage.MONTH_9;
            case DELIVERY_COMPLETED, POSTPARTUM, CHILDCARE_0_7 -> PregnancyStage.BABY_CARE;
            case CLOSED -> null;
        };
    }

    private static final Map<PregnancyStage, String> STAGE_LABELS = Map.of(
            PregnancyStage.MONTH_3, "first trimester",
            PregnancyStage.MONTH_6, "second trimester",
            PregnancyStage.MONTH_9, "third trimester",
            PregnancyStage.BABY_CARE, "after birth");

    private List<String> reasons(PregnancyStage stage, String category, BigDecimal maxPrice) {
        List<String> reasons = new ArrayList<>();
        reasons.add("Matches your current stage: " + STAGE_LABELS.get(stage));
        if (category != null && !category.isBlank()) {
            reasons.add("In your chosen category: " + category);
        }
        if (maxPrice != null) {
            NumberFormat rupees = NumberFormat.getNumberInstance(Locale.forLanguageTag("en-IN"));
            rupees.setMaximumFractionDigits(0);
            reasons.add("Within your budget of ₹" + rupees.format(maxPrice));
        }
        return reasons;
    }
}
