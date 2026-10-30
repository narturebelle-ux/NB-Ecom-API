package com.narturebelle.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.narturebelle.model.Consent;
import com.narturebelle.model.ConsentDocumentType;
import com.narturebelle.model.Journey;
import com.narturebelle.model.JourneyStatus;
import com.narturebelle.model.NecessityTier;
import com.narturebelle.model.PregnancyStage;
import com.narturebelle.model.Product;
import com.narturebelle.model.Role;
import com.narturebelle.model.User;
import com.narturebelle.repository.ConsentRepository;
import com.narturebelle.repository.JourneyRepository;
import com.narturebelle.repository.ProductRecommendationDismissalRepository;
import com.narturebelle.repository.ProductRepository;
import com.narturebelle.repository.UserRepository;
import com.narturebelle.security.FirebaseTokenVerifier;
import com.narturebelle.security.VerifiedFirebaseToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Covers personalised product recommendations (Phase 6). Relies on DataInitializer's sample catalog (test profile). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductRecommendationTest {

    private static final String TOKEN_A = "reco-token-a";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private ConsentRepository consentRepository;
    @Autowired private JourneyRepository journeyRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private ProductRecommendationDismissalRepository dismissalRepository;

    @MockBean private FirebaseTokenVerifier firebaseTokenVerifier;

    private Long userId;

    @BeforeEach
    void setUp() throws Exception {
        dismissalRepository.deleteAll();
        journeyRepository.deleteAll();
        consentRepository.deleteAll();
        userRepository.deleteAll();

        User user = new User();
        user.setFirebaseUid("uid-reco-a");
        user.setEmail("reco-a@example.com");
        user.setEmailVerified(true);
        user.setRoles(Set.of(Role.MOTHER));
        userId = userRepository.save(user).getId();
        for (ConsentDocumentType type : ConsentDocumentType.values()) {
            Consent consent = new Consent();
            consent.setUserId(userId);
            consent.setDocumentType(type);
            consent.setDocumentVersion(1);
            consentRepository.save(consent);
        }

        when(firebaseTokenVerifier.verify(TOKEN_A))
                .thenReturn(new VerifiedFirebaseToken("uid-reco-a", "reco-a@example.com", true, null, null));
    }

    /** EDD 140 days out puts her at gestational week 21 -- second trimester. */
    private void startSecondTrimesterJourney() {
        Journey journey = new Journey();
        journey.setUserId(userId);
        journey.setStatus(JourneyStatus.PREGNANCY);
        journey.setExpectedDeliveryDate(LocalDate.now().plusDays(140));
        journey.setStartedAt(LocalDateTime.now());
        journeyRepository.save(journey);
    }

    private JsonNode recommendations(String query) throws Exception {
        String body = mockMvc.perform(get("/api/v1/me/product-recommendations" + query).header("Authorization", "Bearer " + TOKEN_A))
                .andExpect(status().isOk())
                // UTF-8 explicitly: MockMvc otherwise decodes as ISO-8859-1 and mangles the ₹ in reasons.
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        return objectMapper.readTree(body);
    }

    @Test
    void noJourney_returnsNullStageAndNoItems() throws Exception {
        JsonNode response = recommendations("");
        assertTrue(response.get("stage").isNull());
        assertEquals(0, response.get("items").size());
        assertTrue(response.get("commissionDisclosure").asText().length() > 0);
    }

    @Test
    void secondTrimester_returnsStageProductsWithoutSkipTierOrClinicalFields() throws Exception {
        startSecondTrimesterJourney();
        JsonNode response = recommendations("");

        assertEquals("MONTH_6", response.get("stage").asText());
        Set<Long> expected = new HashSet<>();
        for (Product p : productRepository.findByStage(PregnancyStage.MONTH_6)) {
            if (p.getTier() != NecessityTier.SKIP) expected.add(p.getId());
        }
        Set<Long> actual = new HashSet<>();
        for (JsonNode item : response.get("items")) {
            actual.add(item.get("productId").asLong());
            assertFalse(item.has("tier"));
            assertFalse(item.has("clinicalRationale"));
            assertEquals("Matches your current stage: second trimester", item.get("whyRecommended").get(0).asText());
        }
        assertFalse(expected.isEmpty());
        assertEquals(expected, actual);
    }

    @Test
    void budgetFilter_excludesPricierItems_andExplainsWhy() throws Exception {
        startSecondTrimesterJourney();
        JsonNode response = recommendations("?maxPrice=1000");
        for (JsonNode item : response.get("items")) {
            assertTrue(item.get("price").decimalValue().compareTo(new java.math.BigDecimal("1000")) <= 0);
            assertTrue(item.get("whyRecommended").toString().contains("Within your budget of ₹1,000"));
        }
    }

    @Test
    void dismiss_hidesItem_includeDismissedShowsIt_undismissRestores() throws Exception {
        startSecondTrimesterJourney();
        long productId = recommendations("").get("items").get(0).get("productId").asLong();

        mockMvc.perform(post("/api/v1/me/product-recommendations/" + productId + "/dismiss").header("Authorization", "Bearer " + TOKEN_A))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/me/product-recommendations/" + productId + "/dismiss").header("Authorization", "Bearer " + TOKEN_A))
                .andExpect(status().isNoContent());
        assertEquals(1, dismissalRepository.count());

        assertFalse(recommendations("").get("items").toString().contains("\"productId\":" + productId + ","));
        boolean shownAsDismissed = false;
        for (JsonNode item : recommendations("?includeDismissed=true").get("items")) {
            if (item.get("productId").asLong() == productId) shownAsDismissed = item.get("dismissed").asBoolean();
        }
        assertTrue(shownAsDismissed);

        mockMvc.perform(delete("/api/v1/me/product-recommendations/" + productId + "/dismiss").header("Authorization", "Bearer " + TOKEN_A))
                .andExpect(status().isNoContent());
        assertEquals(0, dismissalRepository.count());
    }

    @Test
    void dismissUnknownProduct_returns404() throws Exception {
        mockMvc.perform(post("/api/v1/me/product-recommendations/999999/dismiss").header("Authorization", "Bearer " + TOKEN_A))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }
}
