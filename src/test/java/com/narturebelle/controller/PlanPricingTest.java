package com.narturebelle.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.narturebelle.model.*;
import com.narturebelle.repository.*;
import com.narturebelle.security.FirebaseTokenVerifier;
import com.narturebelle.security.VerifiedFirebaseToken;
import com.narturebelle.service.EntitlementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Covers duration-based plan pricing (price / 9 per month, months left until the due date) and the EDD lock. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PlanPricingTest {

    private static final String TOKEN = "pricing-token";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private ConsentRepository consentRepository;
    @Autowired private JourneyRepository journeyRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private InstallmentScheduleRepository installmentScheduleRepository;
    @Autowired private EntitlementRepository entitlementRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private EntitlementService entitlementService;

    @MockBean private FirebaseTokenVerifier firebaseTokenVerifier;

    private Long userId;

    @BeforeEach
    void setUp() throws Exception {
        entitlementRepository.deleteAll();
        installmentScheduleRepository.deleteAll();
        paymentRepository.deleteAll();
        orderRepository.deleteAll();
        journeyRepository.deleteAll();
        consentRepository.deleteAll();
        userRepository.deleteAll();

        User user = new User();
        user.setFirebaseUid("uid-pricing");
        user.setEmail("pricing@example.com");
        user.setEmailVerified(true);
        user.setRoles(Set.of(Role.MOTHER));
        userId = userRepository.save(user).getId();
        for (ConsentDocumentType type : Set.of(ConsentDocumentType.TERMS, ConsentDocumentType.PRIVACY)) {
            Consent consent = new Consent();
            consent.setUserId(userId);
            consent.setDocumentType(type);
            consent.setDocumentVersion(1);
            consentRepository.save(consent);
        }
        when(firebaseTokenVerifier.verify(TOKEN))
                .thenReturn(new VerifiedFirebaseToken("uid-pricing", "pricing@example.com", true, null, null));
    }

    private Journey journeyDueIn(int days) {
        Journey journey = new Journey();
        journey.setUserId(userId);
        journey.setStatus(JourneyStatus.PREGNANCY);
        journey.setExpectedDeliveryDate(LocalDate.now().plusDays(days));
        journey.setStartedAt(LocalDateTime.now());
        return journeyRepository.save(journey);
    }

    private JsonNode quote() throws Exception {
        return objectMapper.readTree(mockMvc.perform(get("/api/v1/me/plans/quote").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private JsonNode plan(JsonNode quote, String code) {
        for (JsonNode plan : quote.get("plans")) {
            if (plan.get("code").asText().equals(code)) return plan;
        }
        throw new AssertionError("No plan " + code);
    }

    @Test
    void noDueDate_quotesTheFullNineMonthPlan() throws Exception {
        JsonNode quote = quote();
        assertEquals(9, quote.get("months").asInt());
        assertEquals("FULL_PLAN", quote.get("basis").asText());
        JsonNode standard = plan(quote, "STANDARD");
        assertEquals(0, new BigDecimal("9999").compareTo(standard.get("oneTimePrice").decimalValue()));
        assertEquals(0, new BigDecimal("1111").compareTo(standard.get("monthlyPrice").decimalValue()));
        assertEquals(9, standard.get("totalSessions").asInt());
    }

    @Test
    void dueIn70Days_paysForThreeMonths_andGetsAThirdOfTheSessions() throws Exception {
        journeyDueIn(70); // 70 / 30.44 = 2.3 -> 3 months
        JsonNode quote = quote();
        assertEquals(3, quote.get("months").asInt());
        assertEquals("DUE_DATE", quote.get("basis").asText());
        JsonNode standard = plan(quote, "STANDARD");
        assertEquals(0, new BigDecimal("3333").compareTo(standard.get("oneTimePrice").decimalValue()));
        assertEquals(0, new BigDecimal("9999").compareTo(standard.get("fullOneTimePrice").decimalValue()));
        assertEquals(0, new BigDecimal("5000").compareTo(plan(quote, "CARE").get("oneTimePrice").decimalValue())); // 14999*3/9

        String body = mockMvc.perform(post("/api/v1/orders").header("Authorization", "Bearer " + TOKEN)
                        .header("Idempotency-Key", "pricing-1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planId\":" + standard.get("id").asLong() + ",\"paymentMode\":\"ONE_TIME\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.months").value(3))
                .andReturn().getResponse().getContentAsString();
        Order order = orderRepository.findById(objectMapper.readTree(body).get("id").asLong()).orElseThrow();
        assertEquals(0, new BigDecimal("3333").compareTo(order.getPrice()));

        // Sessions shrink in proportion, at least 1 per professional the plan includes; matches the quote.
        entitlementService.grantForOrder(order);
        int granted = entitlementRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .mapToInt(Entitlement::getTotalSessions).sum();
        assertEquals(standard.get("totalSessions").asInt(), granted);
    }

    @Test
    void afterOrdering_dueDateCanOnlyMoveWithinTolerance() throws Exception {
        Journey journey = journeyDueIn(40);
        long planId = plan(quote(), "STANDARD").get("id").asLong();
        mockMvc.perform(post("/api/v1/orders").header("Authorization", "Bearer " + TOKEN)
                        .header("Idempotency-Key", "pricing-lock").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planId\":" + planId + ",\"paymentMode\":\"ONE_TIME\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.months").value(2));

        // "Pay for a near due date, then move it back" is refused...
        mockMvc.perform(patch("/api/v1/me/journeys/" + journey.getId()).header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedDeliveryDate\":\"" + LocalDate.now().plusDays(200) + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUE_DATE_LOCKED"));

        // ...while a dating-scan sized correction is fine.
        mockMvc.perform(patch("/api/v1/me/journeys/" + journey.getId()).header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedDeliveryDate\":\"" + LocalDate.now().plusDays(50) + "\"}"))
                .andExpect(status().isOk());
    }
}
