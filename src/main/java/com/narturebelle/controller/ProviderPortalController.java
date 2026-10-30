package com.narturebelle.controller;

import com.narturebelle.dto.ProviderPortalDtos.ProductSuggestions;
import com.narturebelle.dto.ProviderPortalDtos.ProviderBooking;
import com.narturebelle.dto.ProviderResponse;
import com.narturebelle.dto.SaveSessionRecommendationsRequest;
import com.narturebelle.dto.SessionProductRecommendationResponse;
import com.narturebelle.model.BookingStatus;
import com.narturebelle.model.Provider;
import com.narturebelle.service.ProviderPortalService;
import com.narturebelle.service.ProviderService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/** The professional portal. Access rules are in ProviderPortalService (verified provider; booking-level also admins). */
@RestController
@RequestMapping("/api/v1/provider")
public class ProviderPortalController {

    private final ProviderPortalService portalService;
    private final ProviderService providerService;

    public ProviderPortalController(ProviderPortalService portalService, ProviderService providerService) {
        this.portalService = portalService;
        this.providerService = providerService;
    }

    @GetMapping("/me")
    public ResponseEntity<ProviderResponse> me() {
        Provider provider = portalService.me();
        return ResponseEntity.ok(ProviderResponse.from(provider, providerService.activeOfferingsFor(provider.getId())));
    }

    @GetMapping("/bookings")
    public ResponseEntity<List<ProviderBooking>> bookings(
            @RequestParam(required = false) BookingStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from) {
        return ResponseEntity.ok(portalService.bookings(status, from));
    }

    @GetMapping("/bookings/{id}/product-suggestions")
    public ResponseEntity<ProductSuggestions> suggestions(@PathVariable Long id) {
        return ResponseEntity.ok(portalService.suggestions(id));
    }

    @GetMapping("/bookings/{id}/product-recommendations")
    public ResponseEntity<List<SessionProductRecommendationResponse>> recommendations(@PathVariable Long id) {
        return ResponseEntity.ok(portalService.sessionRecommendations(id));
    }

    @PutMapping("/bookings/{id}/product-recommendations")
    public ResponseEntity<List<SessionProductRecommendationResponse>> saveRecommendations(
            @PathVariable Long id, @Valid @RequestBody SaveSessionRecommendationsRequest req) {
        return ResponseEntity.ok(portalService.saveSessionRecommendations(id, req));
    }
}
