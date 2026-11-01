package com.narturebelle.controller;

import com.narturebelle.model.Entitlement;
import com.narturebelle.model.PlanCode;
import com.narturebelle.model.Referral;
import com.narturebelle.repository.EntitlementRepository;
import com.narturebelle.repository.PaidOrderRepository;
import com.narturebelle.repository.ReferralRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Events from NB-Ecom-Payments-API: sessions, referral credit and paid votes, all safe to repeat. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "internal.api-token=test-internal-token")
class MoneyEventTest {

    private static final String TOKEN = "test-internal-token";
    private static final long ORDER = 9001L;
    private static final long USER = 8001L;
    private static final String EXPIRES = "2027-11-01T10:15:30.123456";

    @Autowired private MockMvc mockMvc;
    @Autowired private EntitlementRepository entitlementRepository;
    @Autowired private PaidOrderRepository paidOrderRepository;
    @Autowired private ReferralRepository referralRepository;
    @Autowired private com.narturebelle.repository.IdeaVoteRepository ideaVoteRepository;

    @BeforeEach
    void setUp() {
        entitlementRepository.deleteAll();
        paidOrderRepository.deleteAll();
        referralRepository.deleteAll();
    }

    private String paymentSucceeded(String price) {
        return """
                {"eventId":"payment-succeeded:%d","schemaVersion":1,"orderId":%d,"userId":%d,"planCode":"STANDARD",
                 "months":9,"price":%s,"paidAt":"2026-11-01T10:15:30.123456","expiresAt":"%s",
                 "sessions":[{"professionalType":"GYNECOLOGIST","sessionCount":5},{"professionalType":"NUTRITIONIST","sessionCount":4}]}
                """.formatted(ORDER, ORDER, USER, price, EXPIRES);
    }

    private String refundIssued(String orderStatus) {
        return """
                {"eventId":"refund-issued:1","schemaVersion":1,"refundId":1,"orderId":%d,"userId":%d,"amount":100.00,"orderStatus":"%s"}
                """.formatted(ORDER, USER, orderStatus);
    }

    private void send(String path, String body) throws Exception {
        mockMvc.perform(post("/internal/v1/money-events/" + path).header("X-Internal-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }

    @Test
    void noToken_isRefused() throws Exception {
        mockMvc.perform(post("/internal/v1/money-events/payment-succeeded")
                        .contentType(MediaType.APPLICATION_JSON).content(paymentSucceeded("9999.00")))
                .andExpect(status().isUnauthorized());
        assertTrue(entitlementRepository.findAll().isEmpty());
    }

    @Test
    void unknownSchemaVersion_isRefused() throws Exception {
        mockMvc.perform(post("/internal/v1/money-events/payment-succeeded").header("X-Internal-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content(paymentSucceeded("9999.00").replace("\"schemaVersion\":1", "\"schemaVersion\":2")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void paymentSucceeded_grantsWithTheEventsExpiry_notALaterOne() throws Exception {
        send("payment-succeeded", paymentSucceeded("9999.00"));

        List<Entitlement> wallet = entitlementRepository.findByUserIdOrderByCreatedAtDesc(USER);
        assertEquals(2, wallet.size());
        assertEquals(9, wallet.stream().mapToInt(Entitlement::getTotalSessions).sum());
        for (Entitlement entitlement : wallet) {
            assertEquals(LocalDateTime.parse(EXPIRES), entitlement.getExpiresAt());
            assertEquals(PlanCode.STANDARD, entitlement.getPlanCode());
            assertEquals(ORDER, entitlement.getOrderId());
        }
        assertTrue(paidOrderRepository.existsById(ORDER));
    }

    @Test
    void paymentSucceeded_repeated_grantsOnce_andSucceeds() throws Exception {
        send("payment-succeeded", paymentSucceeded("9999.00"));
        send("payment-succeeded", paymentSucceeded("9999.00"));

        assertEquals(2, entitlementRepository.findByUserIdOrderByCreatedAtDesc(USER).size());
        assertEquals(1, paidOrderRepository.count());
    }

    @Test
    void paymentSucceeded_creditsTheReferral_butNotForAFreeOrder() throws Exception {
        referralRepository.save(new Referral(8002L, USER));

        send("payment-succeeded", paymentSucceeded("0"));
        assertNull(referralRepository.findByReferredUserId(USER).orElseThrow().getQualifiedAt());

        send("payment-succeeded", paymentSucceeded("9999.00"));
        LocalDateTime qualifiedAt = referralRepository.findByReferredUserId(USER).orElseThrow().getQualifiedAt();
        assertNotNull(qualifiedAt);

        send("payment-succeeded", paymentSucceeded("9999.00"));
        assertEquals(qualifiedAt, referralRepository.findByReferredUserId(USER).orElseThrow().getQualifiedAt());
    }

    @Test
    void freeOrder_isNotAPaidVote() throws Exception {
        send("payment-succeeded", paymentSucceeded("0"));

        assertEquals(2, entitlementRepository.findByUserIdOrderByCreatedAtDesc(USER).size());
        assertFalse(paidOrderRepository.existsById(ORDER));
    }

    @Test
    void refundArrivingBeforeThePayment_stillEndsPaidStatus() throws Exception {
        send("refund-issued", refundIssued("REFUNDED"));
        send("payment-succeeded", paymentSucceeded("9999.00"));

        assertTrue(paidOrderRepository.findById(ORDER).orElseThrow().isRefunded());
    }

    @Test
    void paymentWithNoSessions_succeedsAndGrantsNothing() throws Exception {
        String body = paymentSucceeded("9999.00").replaceAll("\"sessions\":\\[.*\\]", "\"sessions\":[]");

        send("payment-succeeded", body);

        assertTrue(entitlementRepository.findByUserIdOrderByCreatedAtDesc(USER).isEmpty());
        assertTrue(paidOrderRepository.existsById(ORDER));
    }

    @Test
    void votes_countAsPaidOnlyWhileTheOrderIsUnrefunded_andNeverForAFreeOrder() throws Exception {
        long idea = 70001L;
        ideaVoteRepository.deleteAll();
        ideaVoteRepository.save(new com.narturebelle.model.IdeaVote(idea, USER));

        send("payment-succeeded", paymentSucceeded("0"));
        assertEquals(0L, ((Number) ideaVoteRepository.countVotes(List.of(idea)).get(0)[2]).longValue());

        entitlementRepository.deleteAll();
        send("payment-succeeded", paymentSucceeded("9999.00"));
        assertEquals(1L, ((Number) ideaVoteRepository.countVotes(List.of(idea)).get(0)[2]).longValue());

        send("refund-issued", refundIssued("PARTIALLY_REFUNDED"));
        send("payment-succeeded", paymentSucceeded("9999.00"));
        assertEquals(0L, ((Number) ideaVoteRepository.countVotes(List.of(idea)).get(0)[2]).longValue());

        ideaVoteRepository.deleteAll();
    }

    @Test
    void refundIssued_endsPaidStatus_keepsSessions_andCanRepeat() throws Exception {
        send("payment-succeeded", paymentSucceeded("9999.00"));

        send("refund-issued", refundIssued("PARTIALLY_REFUNDED"));
        send("refund-issued", refundIssued("REFUNDED"));

        assertTrue(paidOrderRepository.findById(ORDER).orElseThrow().isRefunded());
        assertEquals(2, entitlementRepository.findByUserIdOrderByCreatedAtDesc(USER).size());
    }
}
