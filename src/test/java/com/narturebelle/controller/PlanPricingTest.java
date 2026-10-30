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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Plan prices are constant whatever her due date, and she can only hold one plan at a time. */
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

    private long order(long planId, String key) throws Exception {
        String body = mockMvc.perform(post("/api/v1/orders").header("Authorization", "Bearer " + TOKEN)
                        .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planId\":" + planId + ",\"paymentMode\":\"ONE_TIME\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    private long payment(long orderId) throws Exception {
        String body = mockMvc.perform(post("/api/v1/payments/create").header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"orderId\":" + orderId + "}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    private void pay(long paymentId) throws Exception {
        mockMvc.perform(post("/api/v1/payments/" + paymentId + "/mock/succeed").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk());
    }

    @Test
    void priceIsTheSame_whateverTheDueDate() throws Exception {
        JsonNode noJourney = quote();
        assertEquals("FULL_PLAN", noJourney.get("basis").asText());
        assertEquals(9, noJourney.get("months").asInt());

        journeyDueIn(40); // would have been 2 months under the old due-date pricing
        JsonNode nearDueDate = quote();
        assertEquals("FULL_PLAN", nearDueDate.get("basis").asText());
        JsonNode standard = plan(nearDueDate, "STANDARD");
        assertEquals(0, new BigDecimal("9999").compareTo(standard.get("oneTimePrice").decimalValue()));
        assertEquals(0, new BigDecimal("1111").compareTo(standard.get("monthlyPrice").decimalValue()));
        assertEquals(9, standard.get("totalSessions").asInt());
        assertEquals(365, standard.get("validityDays").asInt());

        long orderId = order(standard.get("id").asLong(), "pricing-1");
        Order order = orderRepository.findById(orderId).orElseThrow();
        assertEquals(0, new BigDecimal("9999").compareTo(order.getPrice()));
        assertEquals(9, order.getMonths());

        pay(payment(orderId));
        int granted = entitlementRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .mapToInt(Entitlement::getTotalSessions).sum();
        assertEquals(9, granted);
    }

    @Test
    void oneActivePlanAtATime() throws Exception {
        long planId = plan(quote(), "STANDARD").get("id").asLong();
        pay(payment(order(planId, "one-plan-1")));

        mockMvc.perform(post("/api/v1/orders").header("Authorization", "Bearer " + TOKEN)
                        .header("Idempotency-Key", "one-plan-2").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planId\":" + plan(quote(), "PREMIUM").get("id").asLong() + ",\"paymentMode\":\"ONE_TIME\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLAN_ALREADY_ACTIVE"));

        // Once the 12 months are over she can buy again.
        for (Entitlement entitlement : entitlementRepository.findByUserIdOrderByCreatedAtDesc(userId)) {
            entitlement.setExpiresAt(LocalDateTime.now().minusDays(1));
            entitlementRepository.save(entitlement);
        }
        order(planId, "one-plan-3");
    }

    @Test
    void aNewCheckoutReplacesAnUnfinishedOne_soOnlyOneCanBePaid() throws Exception {
        JsonNode quote = quote();
        long first = order(plan(quote, "STANDARD").get("id").asLong(), "replace-1");
        long firstPayment = payment(first);

        long second = order(plan(quote, "CARE").get("id").asLong(), "replace-2");
        assertEquals(OrderStatus.FAILED, orderRepository.findById(first).orElseThrow().getStatus());
        assertEquals(OrderStatus.PENDING, orderRepository.findById(second).orElseThrow().getStatus());

        // A late success on the replaced order doesn't grant a plan.
        pay(firstPayment);
        assertEquals(OrderStatus.FAILED, orderRepository.findById(first).orElseThrow().getStatus());
        assertTrue(entitlementRepository.findByUserIdOrderByCreatedAtDesc(userId).isEmpty());

        pay(payment(second));
        assertEquals(OrderStatus.PAID, orderRepository.findById(second).orElseThrow().getStatus());
    }

    @Test
    void dueDateCanBeCorrectedAfterBuying() throws Exception {
        Journey journey = journeyDueIn(40);
        order(plan(quote(), "STANDARD").get("id").asLong(), "edd-1");

        mockMvc.perform(patch("/api/v1/me/journeys/" + journey.getId()).header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedDeliveryDate\":\"" + LocalDate.now().plusDays(200) + "\"}"))
                .andExpect(status().isOk());
    }
}
