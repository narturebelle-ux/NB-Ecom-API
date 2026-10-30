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

/** Covers clinically reviewed product health rules: who may review, the review trail, and their effect on recommendations. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductHealthRuleTest {

    private static final String ADMIN = "rule-admin";
    private static final String ADMIN_REVIEWER = "rule-admin-reviewer";
    private static final String MEDICAL = "rule-medical";
    private static final String NUTRITIONIST = "rule-nutritionist";
    private static final String UNVERIFIED_GYN = "rule-unverified-gyn";
    private static final String MOTHER = "rule-mother";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private ConsentRepository consentRepository;
    @Autowired private ProviderRepository providerRepository;
    @Autowired private JourneyRepository journeyRepository;
    @Autowired private HealthProfileRepository healthProfileRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private ProductHealthRuleRepository ruleRepository;

    @MockBean private FirebaseTokenVerifier firebaseTokenVerifier;

    private Long motherId;
    private Product highSugar;
    private Product sugarFree;

    @BeforeEach
    void setUp() throws Exception {
        ruleRepository.deleteAll();
        healthProfileRepository.deleteAll();
        journeyRepository.deleteAll();
        providerRepository.deleteAll();
        consentRepository.deleteAll();
        userRepository.deleteAll();

        user(ADMIN, Set.of(Role.SUPER_ADMIN), null);
        user(ADMIN_REVIEWER, Set.of(Role.SUPER_ADMIN, Role.MEDICAL_REVIEWER), "Dr Both");
        user(MEDICAL, Set.of(Role.MEDICAL_REVIEWER), "Dr Medical");
        provider(user(NUTRITIONIST, Set.of(Role.NUTRITIONIST), null), Role.NUTRITIONIST, ProviderStatus.ACTIVE, "Nita Nutritionist");
        provider(user(UNVERIFIED_GYN, Set.of(Role.GYNECOLOGIST), null), Role.GYNECOLOGIST, ProviderStatus.UNDER_REVIEW, "Gita Gyn");

        motherId = user(MOTHER, Set.of(Role.MOTHER), null);
        for (ConsentDocumentType type : ConsentDocumentType.values()) {
            Consent consent = new Consent();
            consent.setUserId(motherId);
            consent.setDocumentType(type);
            consent.setDocumentVersion(1);
            consentRepository.save(consent);
        }
        Journey journey = new Journey();
        journey.setUserId(motherId);
        journey.setStatus(JourneyStatus.PREGNANCY);
        journey.setExpectedDeliveryDate(LocalDate.now().plusDays(140)); // second trimester -> MONTH_6
        journey.setStartedAt(LocalDateTime.now());
        journeyRepository.save(journey);
        HealthProfile profile = new HealthProfile();
        profile.setUserId(motherId);
        profile.setRiskFactors(new HashSet<>(Set.of(RiskFactor.DIABETES)));
        healthProfileRepository.save(profile);

        List<Product> month6 = productRepository.findByStage(PregnancyStage.MONTH_6).stream()
                .filter(p -> p.getTier() != NecessityTier.SKIP).toList();
        highSugar = tag(month6.get(0), "high-sugar");
        sugarFree = tag(month6.get(month6.size() - 1), "sugar-free");
    }

    private Long user(String token, Set<Role> roles, String name) throws Exception {
        User user = new User();
        user.setFirebaseUid("uid-" + token);
        user.setEmail(token + "@example.com");
        user.setEmailVerified(true);
        user.setRoles(roles);
        Long id = userRepository.save(user).getId();
        when(firebaseTokenVerifier.verify(token))
                .thenReturn(new VerifiedFirebaseToken("uid-" + token, token + "@example.com", true, null, name));
        return id;
    }

    private void provider(Long userId, Role type, ProviderStatus status, String displayName) {
        Provider provider = new Provider();
        provider.setUserId(userId);
        provider.setProfessionalType(type);
        provider.setStatus(status);
        provider.setDisplayName(displayName);
        providerRepository.save(provider);
    }

    private Product tag(Product product, String tag) {
        product.setTags(new HashSet<>(Set.of(tag)));
        return productRepository.save(product);
    }

    private long rule(String token, String body) throws Exception {
        String response = mockMvc.perform(post("/api/v1/admin/product-health-rules").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_CLINICAL_REVIEW"))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private static final String AVOID = "{\"riskFactor\":\"DIABETES\",\"tag\":\"High-Sugar\",\"effect\":\"AVOID\",\"reason\":\"Not suited to a diabetes-friendly diet\"}";
    private static final String PREFER = "{\"riskFactor\":\"DIABETES\",\"tag\":\"sugar-free\",\"effect\":\"PREFER\",\"reason\":\"Sugar-free, which suits a diabetes-friendly diet\"}";

    private JsonNode recommendations() throws Exception {
        return objectMapper.readTree(mockMvc.perform(get("/api/v1/me/product-recommendations").header("Authorization", "Bearer " + MOTHER))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private boolean contains(JsonNode response, Product product) {
        for (JsonNode item : response.get("items")) {
            if (item.get("productId").asLong() == product.getId()) return true;
        }
        return false;
    }

    @Test
    void onlyMedicalReviewersAndVerifiedGynNutritionistNurse_mayReview() throws Exception {
        for (String token : List.of(MOTHER, ADMIN, UNVERIFIED_GYN)) {
            mockMvc.perform(get("/api/v1/review/product-health-rules").header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
        }
        for (String token : List.of(MEDICAL, NUTRITIONIST)) {
            mockMvc.perform(get("/api/v1/review/product-health-rules").header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void pendingRules_haveNoEffect_approvedRulesApply_withReviewTrail() throws Exception {
        long avoid = rule(ADMIN, AVOID);
        long prefer = rule(ADMIN, PREFER);

        JsonNode before = recommendations();
        assertTrue(contains(before, highSugar));

        mockMvc.perform(get("/api/v1/review/product-health-rules").header("Authorization", "Bearer " + NUTRITIONIST))
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(2)))
                .andExpect(jsonPath("$[0].tag").value("high-sugar"));

        mockMvc.perform(post("/api/v1/review/product-health-rules/" + avoid + "/approve").header("Authorization", "Bearer " + NUTRITIONIST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.reviewedByName").value("Nita Nutritionist"))
                .andExpect(jsonPath("$.reviewedByRole").value("NUTRITIONIST"))
                .andExpect(jsonPath("$.reviewedAt").exists());
        mockMvc.perform(post("/api/v1/review/product-health-rules/" + prefer + "/approve").header("Authorization", "Bearer " + MEDICAL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewedByRole").value("MEDICAL_REVIEWER"))
                .andExpect(jsonPath("$.reviewedByName").value("Dr Medical"));

        mockMvc.perform(post("/api/v1/review/product-health-rules/" + avoid + "/approve").header("Authorization", "Bearer " + MEDICAL))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RULE_ALREADY_REVIEWED"));

        JsonNode after = recommendations();
        assertFalse(contains(after, highSugar));
        JsonNode first = after.get("items").get(0);
        assertEquals(sugarFree.getId().longValue(), first.get("productId").asLong());
        assertTrue(first.get("whyRecommended").toString().contains("Sugar-free, which suits a diabetes-friendly diet"));
    }

    @Test
    void withoutHealthDataConsent_approvedRulesDoNotApply() throws Exception {
        long avoid = rule(ADMIN, AVOID);
        mockMvc.perform(post("/api/v1/review/product-health-rules/" + avoid + "/approve").header("Authorization", "Bearer " + MEDICAL))
                .andExpect(status().isOk());
        assertFalse(contains(recommendations(), highSugar));

        mockMvc.perform(post("/api/v1/me/consents/HEALTH_DATA/revoke").header("Authorization", "Bearer " + MOTHER))
                .andExpect(status().isOk());
        assertTrue(contains(recommendations(), highSugar));
    }

    @Test
    void creatorCannotReviewOwnRule_rejectNeedsAReason_andRejectedRulesDoNothing() throws Exception {
        long own = rule(ADMIN_REVIEWER, AVOID);
        mockMvc.perform(post("/api/v1/review/product-health-rules/" + own + "/approve").header("Authorization", "Bearer " + ADMIN_REVIEWER))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RULE_SELF_REVIEW_NOT_ALLOWED"));

        mockMvc.perform(post("/api/v1/review/product-health-rules/" + own + "/reject").header("Authorization", "Bearer " + MEDICAL)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/review/product-health-rules/" + own + "/reject").header("Authorization", "Bearer " + MEDICAL)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Tag too broad\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").value("Tag too broad"));

        assertTrue(contains(recommendations(), highSugar));
        mockMvc.perform(post("/api/v1/review/product-health-rules/999999/approve").header("Authorization", "Bearer " + MEDICAL))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_HEALTH_RULE_NOT_FOUND"));
    }
}
