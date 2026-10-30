package com.narturebelle.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** A user hiding one product from their recommendations. One row per (user, product); un-dismissing deletes it. */
@Entity
@Table(name = "product_recommendation_dismissals", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "product_id"}))
public class ProductRecommendationDismissal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }

    public ProductRecommendationDismissal() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
