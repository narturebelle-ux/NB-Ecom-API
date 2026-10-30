package com.narturebelle.repository;

import com.narturebelle.model.ProductRecommendationDismissal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductRecommendationDismissalRepository extends JpaRepository<ProductRecommendationDismissal, Long> {
    List<ProductRecommendationDismissal> findByUserId(Long userId);
    Optional<ProductRecommendationDismissal> findByUserIdAndProductId(Long userId, Long productId);
    boolean existsByProductId(Long productId);
}
