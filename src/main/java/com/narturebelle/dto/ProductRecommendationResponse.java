package com.narturebelle.dto;

import com.narturebelle.model.Product;

import java.math.BigDecimal;
import java.util.List;

/** Deliberately has no tier (MANDATORY/OPTIONAL/SKIP) and no clinicalRationale -- the store framing is
 * retired, and that rationale text has never had clinical review. */
public class ProductRecommendationResponse {

    private Long productId;
    private String name;
    private String description;
    private BigDecimal price;
    private BigDecimal originalPrice;
    private String imageUrl;
    private String category;
    private List<String> whyRecommended;
    private boolean dismissed;

    public static ProductRecommendationResponse from(Product product, List<String> whyRecommended, boolean dismissed) {
        ProductRecommendationResponse dto = new ProductRecommendationResponse();
        dto.productId = product.getId();
        dto.name = product.getName();
        dto.description = product.getDescription();
        dto.price = product.getPrice();
        dto.originalPrice = product.getOriginalPrice();
        dto.imageUrl = product.getImageUrl();
        dto.category = product.getCategory();
        dto.whyRecommended = whyRecommended;
        dto.dismissed = dismissed;
        return dto;
    }

    public Long getProductId() { return productId; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public BigDecimal getPrice() { return price; }
    public BigDecimal getOriginalPrice() { return originalPrice; }
    public String getImageUrl() { return imageUrl; }
    public String getCategory() { return category; }
    public List<String> getWhyRecommended() { return whyRecommended; }
    public boolean isDismissed() { return dismissed; }
}
