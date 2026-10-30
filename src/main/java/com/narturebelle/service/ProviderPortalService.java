package com.narturebelle.service;

import com.narturebelle.dto.HealthProfileResponse;
import com.narturebelle.dto.ProviderPortalDtos.MotherSummary;
import com.narturebelle.dto.ProviderPortalDtos.MotherWithHealth;
import com.narturebelle.dto.ProviderPortalDtos.ProductSuggestions;
import com.narturebelle.dto.ProviderPortalDtos.ProviderBooking;
import com.narturebelle.dto.SaveSessionRecommendationsRequest;
import com.narturebelle.dto.SessionProductRecommendationResponse;
import com.narturebelle.exception.ProductNotFoundException;
import com.narturebelle.exception.ProviderNotFoundException;
import com.narturebelle.exception.SessionRecommendationException;
import com.narturebelle.model.*;
import com.narturebelle.repository.*;
import com.narturebelle.security.AuditService;
import com.narturebelle.security.AuthorizationService;
import com.narturebelle.security.FirebaseUserPrincipal;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * The professional portal: a verified professional sees her sessions, and in a session picks products for
 * the mother (ESSENTIAL / OPTIONAL, with a note). The mother sees the picks once the session is COMPLETED.
 * Booking-level endpoints are for the booking's assigned provider (VERIFIED/ACTIVE) or an admin.
 */
@Service
public class ProviderPortalService {

    /** Picks can still be edited this long after the session is marked complete (e.g. to finish notes afterwards). */
    static final long EDIT_WINDOW_HOURS_AFTER_COMPLETION = 48;
    private static final Set<ProviderStatus> VERIFIED = EnumSet.of(ProviderStatus.VERIFIED, ProviderStatus.ACTIVE);

    private final AuthorizationService authorizationService;
    private final ProviderRepository providerRepository;
    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final ServiceOfferingRepository serviceOfferingRepository;
    private final ProductRepository productRepository;
    private final JourneyRepository journeyRepository;
    private final SessionProductRecommendationRepository recommendationRepository;
    private final ProductRecommendationService productRecommendationService;
    private final HealthProfileService healthProfileService;
    private final AuditService auditService;

    public ProviderPortalService(AuthorizationService authorizationService,
                                 ProviderRepository providerRepository,
                                 BookingRepository bookingRepository,
                                 UserRepository userRepository,
                                 ServiceOfferingRepository serviceOfferingRepository,
                                 ProductRepository productRepository,
                                 JourneyRepository journeyRepository,
                                 SessionProductRecommendationRepository recommendationRepository,
                                 ProductRecommendationService productRecommendationService,
                                 HealthProfileService healthProfileService,
                                 AuditService auditService) {
        this.authorizationService = authorizationService;
        this.providerRepository = providerRepository;
        this.bookingRepository = bookingRepository;
        this.userRepository = userRepository;
        this.serviceOfferingRepository = serviceOfferingRepository;
        this.productRepository = productRepository;
        this.journeyRepository = journeyRepository;
        this.recommendationRepository = recommendationRepository;
        this.productRecommendationService = productRecommendationService;
        this.healthProfileService = healthProfileService;
        this.auditService = auditService;
    }

    /** The caller's own provider profile, whatever its status -- 404 PROVIDER_NOT_FOUND if she isn't a provider. */
    public Provider me() {
        Long userId = authorizationService.currentPrincipal().userId();
        return Optional.ofNullable(userId).flatMap(providerRepository::findByUserId)
                .orElseThrow(() -> new ProviderNotFoundException("You don't have a provider profile."));
    }

    /** The caller's sessions (VERIFIED/ACTIVE providers only), scheduled from {@code from} on, soonest first. */
    public List<ProviderBooking> bookings(BookingStatus status, LocalDateTime from) {
        Provider provider = requireVerifiedProvider();
        LocalDateTime since = from != null ? from : LocalDateTime.now().minusDays(30);
        return bookingRepository.findByProviderIdOrderByCreatedAtDesc(provider.getId()).stream()
                .filter(b -> status == null || b.getStatus() == status)
                .filter(b -> !b.getScheduledStart().isBefore(since))
                .sorted(Comparator.comparing(Booking::getScheduledStart))
                .map(b -> {
                    ServiceOffering offering = serviceOfferingRepository.findById(b.getServiceOfferingId()).orElse(null);
                    return new ProviderBooking(b.getId(), b.getStatus(), b.getScheduledStart(), b.getScheduledEnd(),
                            offering != null ? offering.getModality() : null, offering != null ? offering.getName() : null,
                            motherSummary(b.getUserId()));
                })
                .toList();
    }

    /** What she might need, for the professional to pick from. Health details only under HEALTH_DATA v2+ (audited). */
    public ProductSuggestions suggestions(Long bookingId) {
        Booking booking = requireAssignedBooking(bookingId);
        MotherSummary mother = motherSummary(booking.getUserId());
        Optional<HealthProfile> health = healthProfileService.forCareTeam(booking.getUserId(), booking.getId());
        Set<RiskFactor> visibleRisks = health.map(HealthProfile::getRiskFactors).orElse(Set.of());
        var suggestions = productRecommendationService.careTeamSuggestions(mother.stage(), visibleRisks);
        return new ProductSuggestions(
                new MotherWithHealth(mother.firstName(), mother.stage(), mother.gestationalWeek(),
                        health.map(HealthProfileResponse::from).orElse(null)),
                suggestions.items(), suggestions.avoided());
    }

    public List<SessionProductRecommendationResponse> sessionRecommendations(Long bookingId) {
        Booking booking = requireAssignedBooking(bookingId);
        return toResponses(recommendationRepository.findByBookingIdOrderByIdAsc(booking.getId()), Map.of(booking.getId(), booking));
    }

    /** Full replacement. Open while IN_PROGRESS and for 48h after COMPLETED; 409 SESSION_RECOMMENDATIONS_CLOSED otherwise. */
    @Transactional
    public List<SessionProductRecommendationResponse> saveSessionRecommendations(Long bookingId, SaveSessionRecommendationsRequest req) {
        Booking booking = requireAssignedBooking(bookingId);
        boolean open = booking.getStatus() == BookingStatus.IN_PROGRESS
                || (booking.getStatus() == BookingStatus.COMPLETED && booking.getCompletedAt() != null
                    && booking.getCompletedAt().plusHours(EDIT_WINDOW_HOURS_AFTER_COMPLETION).isAfter(LocalDateTime.now()));
        if (!open) {
            throw new SessionRecommendationException(409, "SESSION_RECOMMENDATIONS_CLOSED",
                    "Products can be recommended during the session and up to " + EDIT_WINDOW_HOURS_AFTER_COMPLETION
                            + " hours after it's completed.");
        }

        // Last entry wins if a product is listed twice.
        Map<Long, SaveSessionRecommendationsRequest.Item> byProduct = new LinkedHashMap<>();
        for (SaveSessionRecommendationsRequest.Item item : req.getItems()) {
            if (!productRepository.existsById(item.getProductId())) {
                throw new ProductNotFoundException("Product " + item.getProductId() + " not found.");
            }
            byProduct.put(item.getProductId(), item);
        }

        recommendationRepository.deleteByBookingId(booking.getId());
        recommendationRepository.flush(); // deletes before inserts, or the (booking, product) unique key trips
        List<SessionProductRecommendation> saved = new ArrayList<>();
        for (SaveSessionRecommendationsRequest.Item item : byProduct.values()) {
            SessionProductRecommendation rec = new SessionProductRecommendation();
            rec.setBookingId(booking.getId());
            rec.setMotherUserId(booking.getUserId());
            rec.setProviderId(booking.getProviderId());
            rec.setProductId(item.getProductId());
            rec.setPriority(item.getPriority());
            rec.setNote(item.getNote() != null && !item.getNote().isBlank() ? item.getNote().trim() : null);
            saved.add(recommendationRepository.save(rec));
        }
        auditService.record("SESSION_PRODUCTS_RECOMMENDED", "BOOKING", String.valueOf(booking.getId()),
                "Professional saved product recommendations for a session", Map.of("count", saved.size()));
        return toResponses(saved, Map.of(booking.getId(), booking));
    }

    /** The mother's view: picks from her COMPLETED sessions only, newest session first, essentials first within one. */
    public List<SessionProductRecommendationResponse> forMother(Long userId, Long bookingId) {
        if (bookingId != null) {
            bookingRepository.findById(bookingId).filter(b -> b.getUserId().equals(userId))
                    .orElseThrow(() -> new SessionRecommendationException(404, "BOOKING_NOT_FOUND", "Booking " + bookingId + " not found."));
        }
        List<SessionProductRecommendation> recs = recommendationRepository.findByMotherUserId(userId).stream()
                .filter(r -> bookingId == null || r.getBookingId().equals(bookingId))
                .toList();
        Map<Long, Booking> bookings = new HashMap<>();
        for (SessionProductRecommendation rec : recs) {
            bookings.computeIfAbsent(rec.getBookingId(), id -> bookingRepository.findById(id).orElse(null));
        }
        List<SessionProductRecommendation> visible = recs.stream()
                .filter(r -> bookings.get(r.getBookingId()) != null && bookings.get(r.getBookingId()).getStatus() == BookingStatus.COMPLETED)
                .sorted(Comparator.comparing((SessionProductRecommendation r) -> bookings.get(r.getBookingId()).getScheduledStart()).reversed()
                        .thenComparing(r -> r.getPriority() == RecommendationPriority.ESSENTIAL ? 0 : 1)
                        .thenComparing(SessionProductRecommendation::getId))
                .toList();
        return toResponses(visible, bookings);
    }

    private List<SessionProductRecommendationResponse> toResponses(List<SessionProductRecommendation> recs, Map<Long, Booking> bookings) {
        Map<Long, Provider> providers = new HashMap<>();
        return recs.stream().map(rec -> {
            Provider provider = providers.computeIfAbsent(rec.getProviderId(), id -> providerRepository.findById(id).orElse(null));
            Booking booking = bookings.get(rec.getBookingId());
            return SessionProductRecommendationResponse.from(rec, productRepository.findById(rec.getProductId()).orElse(null),
                    provider, booking != null ? booking.getScheduledStart() : null);
        }).toList();
    }

    private MotherSummary motherSummary(Long motherUserId) {
        String firstName = userRepository.findById(motherUserId).map(User::getFirstName).orElse(null);
        PregnancyStage stage = productRecommendationService.currentStage(motherUserId);
        Integer week = journeyRepository.findByUserIdAndStatusNot(motherUserId, JourneyStatus.CLOSED)
                .map(j -> GestationalMath.gestationalWeek(j.getStatus(), j.getExpectedDeliveryDate()))
                .orElse(null);
        return new MotherSummary(firstName, stage, week);
    }

    private Provider requireVerifiedProvider() {
        Long userId = authorizationService.currentPrincipal().userId();
        return Optional.ofNullable(userId).flatMap(providerRepository::findByUserId)
                .filter(p -> VERIFIED.contains(p.getStatus()))
                .orElseThrow(() -> new AccessDeniedException("Only verified professionals can use the provider portal."));
    }

    /** The booking's assigned (verified) provider, or an admin. 404 if the booking doesn't exist, 403 if it isn't theirs. */
    private Booking requireAssignedBooking(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new SessionRecommendationException(404, "BOOKING_NOT_FOUND", "Booking " + bookingId + " not found."));
        FirebaseUserPrincipal principal = authorizationService.currentPrincipal();
        if (principal.isAdmin()) {
            return booking;
        }
        Provider caller = requireVerifiedProvider();
        if (!caller.getId().equals(booking.getProviderId())) {
            throw new AccessDeniedException("This session isn't assigned to you.");
        }
        return booking;
    }
}
