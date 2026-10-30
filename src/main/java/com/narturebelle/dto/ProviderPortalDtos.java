package com.narturebelle.dto;

import com.narturebelle.model.BookingStatus;
import com.narturebelle.model.Modality;
import com.narturebelle.model.PregnancyStage;
import com.narturebelle.service.ProductRecommendationService.AvoidedProduct;

import java.time.LocalDateTime;
import java.util.List;

/** Response shapes for the professional portal (/api/v1/provider/**). */
public final class ProviderPortalDtos {

    private ProviderPortalDtos() {}

    /** Only what the professional needs to prepare: first name, stage, gestational week. */
    public record MotherSummary(String firstName, PregnancyStage stage, Integer gestationalWeek) {}

    public record ProviderBooking(Long id, BookingStatus status, LocalDateTime scheduledStart, LocalDateTime scheduledEnd,
                                  Modality modality, String serviceName, MotherSummary mother) {}

    /** healthProfile is null unless she's granted the current HEALTH_DATA version (v2+), which covers care-team access. */
    public record MotherWithHealth(String firstName, PregnancyStage stage, Integer gestationalWeek, HealthProfileResponse healthProfile) {}

    public record ProductSuggestions(MotherWithHealth mother, List<ProductRecommendationResponse> items, List<AvoidedProduct> avoided) {}
}
