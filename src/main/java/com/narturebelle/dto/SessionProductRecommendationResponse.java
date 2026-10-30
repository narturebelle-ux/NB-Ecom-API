package com.narturebelle.dto;

import com.narturebelle.model.Product;
import com.narturebelle.model.Provider;
import com.narturebelle.model.RecommendationPriority;
import com.narturebelle.model.Role;
import com.narturebelle.model.SessionProductRecommendation;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class SessionProductRecommendationResponse {

    private Long id;
    private Long bookingId;
    private LocalDateTime sessionDate;
    private Long productId;
    private String name;
    private String description;
    private String imageUrl;
    private BigDecimal price;
    private BigDecimal originalPrice;
    private String category;
    private RecommendationPriority priority;
    private String note;
    private RecommendedBy recommendedBy;
    private LocalDateTime createdAt;

    public static SessionProductRecommendationResponse from(SessionProductRecommendation rec, Product product, Provider provider,
                                                            LocalDateTime sessionDate) {
        SessionProductRecommendationResponse dto = new SessionProductRecommendationResponse();
        dto.id = rec.getId();
        dto.bookingId = rec.getBookingId();
        dto.sessionDate = sessionDate;
        dto.productId = rec.getProductId();
        if (product != null) {
            dto.name = product.getName();
            dto.description = product.getDescription();
            dto.imageUrl = product.getImageUrl();
            dto.price = product.getPrice();
            dto.originalPrice = product.getOriginalPrice();
            dto.category = product.getCategory();
        }
        dto.priority = rec.getPriority();
        dto.note = rec.getNote();
        if (provider != null) {
            dto.recommendedBy = new RecommendedBy(provider.getId(), provider.getDisplayName(), provider.getProfessionalType());
        }
        dto.createdAt = rec.getCreatedAt();
        return dto;
    }

    public record RecommendedBy(Long providerId, String displayName, Role professionalType) {}

    public Long getId() { return id; }
    public Long getBookingId() { return bookingId; }
    public LocalDateTime getSessionDate() { return sessionDate; }
    public Long getProductId() { return productId; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getImageUrl() { return imageUrl; }
    public BigDecimal getPrice() { return price; }
    public BigDecimal getOriginalPrice() { return originalPrice; }
    public String getCategory() { return category; }
    public RecommendationPriority getPriority() { return priority; }
    public String getNote() { return note; }
    public RecommendedBy getRecommendedBy() { return recommendedBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
