package com.narturebelle.dto;

import com.narturebelle.model.PregnancyStage;

import java.util.List;

public class ProductRecommendationsResponse {

    private PregnancyStage stage;
    private String commissionDisclosure;
    private List<ProductRecommendationResponse> items;

    /** stage is null (and items empty) when the user has no active journey to derive a stage from. */
    public static ProductRecommendationsResponse of(PregnancyStage stage, String commissionDisclosure,
                                                    List<ProductRecommendationResponse> items) {
        ProductRecommendationsResponse dto = new ProductRecommendationsResponse();
        dto.stage = stage;
        dto.commissionDisclosure = commissionDisclosure;
        dto.items = items;
        return dto;
    }

    public PregnancyStage getStage() { return stage; }
    public String getCommissionDisclosure() { return commissionDisclosure; }
    public List<ProductRecommendationResponse> getItems() { return items; }
}
