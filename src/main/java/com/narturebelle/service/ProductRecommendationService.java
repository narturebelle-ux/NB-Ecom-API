package com.narturebelle.service;

import com.narturebelle.dto.ProductRecommendationResponse;
import com.narturebelle.dto.ProductRecommendationsResponse;
import com.narturebelle.exception.ProductNotFoundException;
import com.narturebelle.model.Journey;
import com.narturebelle.model.JourneyStatus;
import com.narturebelle.model.NecessityTier;
import com.narturebelle.model.PregnancyStage;
import com.narturebelle.model.Product;
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
 * Product recommendations (Phase 6) over the existing Product catalog. Reasons are non-clinical only
 * (stage, chosen category, budget) -- never Product.clinicalRationale, which has had no clinical review,
 * and never the onboarding intake's risk factors. SKIP-tier products are never recommended.
 */
@Service
public class ProductRecommendationService {

    private final ProductRepository productRepository;
    private final ProductRecommendationDismissalRepository dismissalRepository;
    private final JourneyRepository journeyRepository;
    private final AuditService auditService;
    private final String commissionDisclosure;

    public ProductRecommendationService(ProductRepository productRepository,
                                        ProductRecommendationDismissalRepository dismissalRepository,
                                        JourneyRepository journeyRepository,
                                        AuditService auditService,
                                        @Value("${narturebelle.recommendations.commission-disclosure:"
                                                + "NartureBelle may earn a commission if you buy through these recommendations. "
                                                + "It never affects what we recommend.}") String commissionDisclosure) {
        this.productRepository = productRepository;
        this.dismissalRepository = dismissalRepository;
        this.journeyRepository = journeyRepository;
        this.auditService = auditService;
        this.commissionDisclosure = commissionDisclosure;
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

        List<ProductRecommendationResponse> items = productRepository.findByStage(stage).stream()
                .filter(p -> p.getTier() != NecessityTier.SKIP)
                .filter(p -> category == null || category.isBlank() || category.equalsIgnoreCase(p.getCategory()))
                .filter(p -> maxPrice == null || p.getPrice().compareTo(maxPrice) <= 0)
                .filter(p -> includeDismissed || !dismissed.contains(p.getId()))
                // Essentials first, but the tier itself is never exposed.
                .sorted(Comparator.comparing((Product p) -> p.getTier() == NecessityTier.MANDATORY ? 0 : 1)
                        .thenComparing(Product::getPrice))
                .map(p -> ProductRecommendationResponse.from(p, reasons(stage, category, maxPrice), dismissed.contains(p.getId())))
                .toList();
        return ProductRecommendationsResponse.of(stage, commissionDisclosure, items);
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
