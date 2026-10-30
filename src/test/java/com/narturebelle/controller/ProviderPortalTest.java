package com.narturebelle.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.narturebelle.model.*;
import com.narturebelle.repository.*;
import com.narturebelle.security.FirebaseTokenVerifier;
import com.narturebelle.security.VerifiedFirebaseToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Covers the professional portal, session product picks, HEALTH_DATA v2 visibility, and the mother's care recommendations. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProviderPortalTest {

    private static final String MOTHER = "portal-mother";
    private static final String GYN = "portal-gyn";
    private static final String OTHER_GYN = "portal-other-gyn";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private ConsentRepository consentRepository;
    @Autowired private ProviderRepository providerRepository;
    @Autowired private ServiceOfferingRepository serviceOfferingRepository;
    @Autowired private ProviderSlotRepository providerSlotRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private JourneyRepository journeyRepository;
    @Autowired private HealthProfileRepository healthProfileRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private ProductHealthRuleRepository ruleRepository;
    @Autowired private SessionProductRecommendationRepository recommendationRepository;

    @MockBean private FirebaseTokenVerifier firebaseTokenVerifier;

    private Long motherId;
    private Long bookingId;
    private Product highSugar;
    private Product other;

    @BeforeEach
    void setUp() throws Exception {
        recommendationRepository.deleteAll();
        ruleRepository.deleteAll();
        bookingRepository.deleteAll();
        providerSlotRepository.deleteAll();
        serviceOfferingRepository.deleteAll();
        providerRepository.deleteAll();
        healthProfileRepository.deleteAll();
        journeyRepository.deleteAll();
        consentRepository.deleteAll();
        userRepository.deleteAll();

        motherId = user(MOTHER, Role.MOTHER, "Asha");
        grant(ConsentDocumentType.TERMS, 1);
        grant(ConsentDocumentType.PRIVACY, 1);
        Journey journey = new Journey();
        journey.setUserId(motherId);
        journey.setStatus(JourneyStatus.PREGNANCY);
        journey.setExpectedDeliveryDate(LocalDate.now().plusDays(140));
        journey.setStartedAt(LocalDateTime.now());
        journeyRepository.save(journey);
        HealthProfile profile = new HealthProfile();
        profile.setUserId(motherId);
        profile.setRiskFactors(new HashSet<>(Set.of(RiskFactor.DIABETES)));
        healthProfileRepository.save(profile);

        Long providerId = provider(user(GYN, Role.GYNECOLOGIST, null), "Dr Sample Gyn");
        provider(user(OTHER_GYN, Role.GYNECOLOGIST, null), "Dr Other");

        ServiceOffering offering = new ServiceOffering();
        offering.setProviderId(providerId);
        offering.setName("Online consultation");
        offering.setModality(Modality.VIRTUAL);
        offering.setDurationMinutes(30);
        offering.setActive(true);
        Long offeringId = serviceOfferingRepository.save(offering).getId();
        ProviderSlot slot = new ProviderSlot();
        slot.setProviderId(providerId);
        slot.setStartAt(LocalDateTime.now().plusHours(1));
        slot.setEndAt(LocalDateTime.now().plusHours(2));
        slot.setStatus(SlotStatus.BOOKED);
        Long slotId = providerSlotRepository.save(slot).getId();

        Booking booking = new Booking();
        booking.setUserId(motherId);
        booking.setProviderId(providerId);
        booking.setServiceOfferingId(offeringId);
        booking.setSlotId(slotId);
        booking.setProfessionalType(Role.GYNECOLOGIST);
        booking.setStatus(BookingStatus.IN_PROGRESS);
        booking.setScheduledStart(LocalDateTime.now().plusHours(1));
        booking.setScheduledEnd(LocalDateTime.now().plusHours(2));
        bookingId = bookingRepository.save(booking).getId();

        List<Product> month6 = productRepository.findByStage(PregnancyStage.MONTH_6).stream()
                .filter(p -> p.getTier() != NecessityTier.SKIP).toList();
        highSugar = month6.get(0);
        highSugar.setTags(new HashSet<>(Set.of("high-sugar")));
        productRepository.save(highSugar);
        other = month6.get(1);

        ProductHealthRule rule = new ProductHealthRule();
        rule.setRiskFactor(RiskFactor.DIABETES);
        rule.setTag("high-sugar");
        rule.setEffect(RuleEffect.AVOID);
        rule.setReason("Not suited to a diabetes-friendly diet");
        rule.setStatus(RuleReviewStatus.APPROVED);
        rule.setCreatedByUserId(motherId);
        ruleRepository.save(rule);
    }

    private Long user(String token, Role role, String firstName) throws Exception {
        User user = new User();
        user.setFirebaseUid("uid-" + token);
        user.setEmail(token + "@example.com");
        user.setEmailVerified(true);
        user.setFirstName(firstName);
        user.setRoles(Set.of(role));
        Long id = userRepository.save(user).getId();
        when(firebaseTokenVerifier.verify(token))
                .thenReturn(new VerifiedFirebaseToken("uid-" + token, token + "@example.com", true, null, null));
        return id;
    }

    private Long provider(Long userId, String displayName) {
        Provider provider = new Provider();
        provider.setUserId(userId);
        provider.setProfessionalType(Role.GYNECOLOGIST);
        provider.setStatus(ProviderStatus.ACTIVE);
        provider.setDisplayName(displayName);
        return providerRepository.save(provider).getId();
    }

    private void grant(ConsentDocumentType type, int version) {
        Consent consent = new Consent();
        consent.setUserId(motherId);
        consent.setDocumentType(type);
        consent.setDocumentVersion(version);
        consentRepository.save(consent);
    }

    private JsonNode json(String token, String path) throws Exception {
        return objectMapper.readTree(mockMvc.perform(get(path).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private boolean listsProduct(JsonNode array, Product product) {
        for (JsonNode node : array) {
            if (node.get("productId").asLong() == product.getId()) return true;
        }
        return false;
    }

    private void save(String body) throws Exception {
        mockMvc.perform(put("/api/v1/provider/bookings/" + bookingId + "/product-recommendations").header("Authorization", "Bearer " + GYN)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }

    private void complete(LocalDateTime at) {
        Booking booking = bookingRepository.findById(bookingId).orElseThrow();
        booking.setStatus(BookingStatus.COMPLETED);
        booking.setCompletedAt(at);
        bookingRepository.save(booking);
    }

    @Test
    void me_andBookings_forTheAssignedProvider() throws Exception {
        mockMvc.perform(get("/api/v1/provider/me").header("Authorization", "Bearer " + GYN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Dr Sample Gyn"));
        mockMvc.perform(get("/api/v1/provider/me").header("Authorization", "Bearer " + MOTHER))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROVIDER_NOT_FOUND"));

        mockMvc.perform(get("/api/v1/provider/bookings").header("Authorization", "Bearer " + GYN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(bookingId))
                .andExpect(jsonPath("$[0].serviceName").value("Online consultation"))
                .andExpect(jsonPath("$[0].mother.firstName").value("Asha"))
                .andExpect(jsonPath("$[0].mother.stage").value("MONTH_6"))
                .andExpect(jsonPath("$[0].mother.gestationalWeek").value(21));
        mockMvc.perform(get("/api/v1/provider/bookings").header("Authorization", "Bearer " + OTHER_GYN))
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(0)));
        mockMvc.perform(get("/api/v1/provider/bookings").header("Authorization", "Bearer " + MOTHER))
                .andExpect(status().isForbidden());
    }

    @Test
    void suggestions_showHealthAndAvoidedProducts_onlyUnderHealthDataV2() throws Exception {
        grant(ConsentDocumentType.HEALTH_DATA, 1);
        JsonNode v1 = json(GYN, "/api/v1/provider/bookings/" + bookingId + "/product-suggestions");
        assertTrue(v1.get("mother").get("healthProfile").isNull());
        assertEquals(0, v1.get("avoided").size());
        assertTrue(listsProduct(v1.get("items"), highSugar));

        grant(ConsentDocumentType.HEALTH_DATA, 2);
        JsonNode v2 = json(GYN, "/api/v1/provider/bookings/" + bookingId + "/product-suggestions");
        assertEquals("DIABETES", v2.get("mother").get("healthProfile").get("riskFactors").get(0).asText());
        assertFalse(listsProduct(v2.get("items"), highSugar));
        assertEquals(highSugar.getId().longValue(), v2.get("avoided").get(0).get("productId").asLong());
        assertEquals("Not suited to a diabetes-friendly diet", v2.get("avoided").get(0).get("reason").asText());

        mockMvc.perform(get("/api/v1/provider/bookings/" + bookingId + "/product-suggestions").header("Authorization", "Bearer " + OTHER_GYN))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/provider/bookings/999999/product-suggestions").header("Authorization", "Bearer " + GYN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BOOKING_NOT_FOUND"));
    }

    @Test
    void picks_areReplacedWhole_andReachTheMotherOnlyAfterCompletion() throws Exception {
        save("{\"items\":[{\"productId\":" + highSugar.getId() + ",\"priority\":\"OPTIONAL\"}]}");
        save("{\"items\":[{\"productId\":" + other.getId() + ",\"priority\":\"OPTIONAL\",\"note\":\"Nice to have\"},"
                + "{\"productId\":" + highSugar.getId() + ",\"priority\":\"ESSENTIAL\",\"note\":\"Twice a day\"}]}");
        assertEquals(2, recommendationRepository.count());

        mockMvc.perform(get("/api/v1/me/care-recommendations").header("Authorization", "Bearer " + MOTHER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(0)));

        complete(LocalDateTime.now());
        JsonNode recs = json(MOTHER, "/api/v1/me/care-recommendations?bookingId=" + bookingId);
        assertEquals(2, recs.size());
        assertEquals("ESSENTIAL", recs.get(0).get("priority").asText());
        assertEquals("Twice a day", recs.get(0).get("note").asText());
        assertEquals("Dr Sample Gyn", recs.get(0).get("recommendedBy").get("displayName").asText());
        assertEquals("GYNECOLOGIST", recs.get(0).get("recommendedBy").get("professionalType").asText());
        assertTrue(recs.get(0).has("price"));
        assertTrue(recs.get(0).has("sessionDate"));

        // Still editable within 48h of completion.
        save("{\"items\":[{\"productId\":" + other.getId() + ",\"priority\":\"ESSENTIAL\"}]}");
        assertEquals(1, json(MOTHER, "/api/v1/me/care-recommendations").size());
    }

    @Test
    void picks_closedBeforeStartAndAfterTheWindow() throws Exception {
        Booking booking = bookingRepository.findById(bookingId).orElseThrow();
        booking.setStatus(BookingStatus.BOOKED);
        bookingRepository.save(booking);
        String body = "{\"items\":[{\"productId\":" + other.getId() + ",\"priority\":\"ESSENTIAL\"}]}";
        mockMvc.perform(put("/api/v1/provider/bookings/" + bookingId + "/product-recommendations").header("Authorization", "Bearer " + GYN)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SESSION_RECOMMENDATIONS_CLOSED"));

        complete(LocalDateTime.now().minusDays(3));
        mockMvc.perform(put("/api/v1/provider/bookings/" + bookingId + "/product-recommendations").header("Authorization", "Bearer " + GYN)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void revokingHealthData_revokesEveryVersion() throws Exception {
        grant(ConsentDocumentType.HEALTH_DATA, 1);
        grant(ConsentDocumentType.HEALTH_DATA, 2);
        mockMvc.perform(post("/api/v1/me/consents/HEALTH_DATA/revoke").header("Authorization", "Bearer " + MOTHER))
                .andExpect(status().isOk());
        assertTrue(consentRepository.findByUserIdAndDocumentTypeAndRevokedAtIsNull(motherId, ConsentDocumentType.HEALTH_DATA).isEmpty());
    }
}
