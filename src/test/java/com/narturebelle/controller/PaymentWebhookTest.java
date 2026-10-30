package com.narturebelle.controller;

import com.narturebelle.model.Role;
import com.narturebelle.model.User;
import com.narturebelle.repository.EntitlementRepository;
import com.narturebelle.repository.OrderRepository;
import com.narturebelle.repository.PaymentRepository;
import com.narturebelle.repository.PaymentWebhookEventRepository;
import com.narturebelle.repository.UserRepository;
import com.narturebelle.security.FirebaseTokenVerifier;
import com.narturebelle.security.VerifiedFirebaseToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Covers 2.4/2.5: mock checkout really exercises the webhook path, idempotent entitlement grants, signature verification. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PaymentWebhookTest {

    private static final String TOKEN = "payment-token";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentWebhookEventRepository paymentWebhookEventRepository;

    @Autowired
    private EntitlementRepository entitlementRepository;

    @Value("${payments.mock.webhook-secret}")
    private String webhookSecret;

    @MockBean
    private FirebaseTokenVerifier firebaseTokenVerifier;

    @BeforeEach
    void setUp() throws Exception {
        entitlementRepository.deleteAll();
        paymentWebhookEventRepository.deleteAll();
        paymentRepository.deleteAll();
        orderRepository.deleteAll();
        userRepository.deleteAll();

        User user = new User();
        user.setFirebaseUid("uid-payment");
        user.setEmail("payment@example.com");
        user.setEmailVerified(true);
        user.setRoles(Set.of(Role.MOTHER));
        userRepository.save(user);

        when(firebaseTokenVerifier.verify(TOKEN))
                .thenReturn(new VerifiedFirebaseToken("uid-payment", "payment@example.com", true, null, null));
    }

    private Long createOrderAndPayment(String idempotencyKey) throws Exception {
        String orderBody = mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + TOKEN)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planId\":1,\"paymentMode\":\"ONE_TIME\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Long orderId = Long.valueOf(orderBody.replaceAll(".*\"id\":(\\d+).*", "$1"));

        String paymentBody = mockMvc.perform(post("/api/v1/payments/create")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":" + orderId + "}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return Long.valueOf(paymentBody.replaceAll(".*\"id\":(\\d+).*", "$1"));
    }

    @Test
    void mockSucceed_marksOrderPaidAndGrantsEntitlements() throws Exception {
        Long paymentId = createOrderAndPayment("webhook-key-1");

        mockMvc.perform(post("/api/v1/payments/" + paymentId + "/mock/succeed")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAYMENT_SUCCEEDED"));

        org.junit.jupiter.api.Assertions.assertEquals("SUCCEEDED",
                paymentRepository.findById(paymentId).orElseThrow().getStatus().name());
        org.junit.jupiter.api.Assertions.assertEquals("PAID",
                orderRepository.findAll().get(0).getStatus().name());

        // Standard plan allocation: GYNECOLOGIST 5, NUTRITIONIST 4 -> 2 entitlement rows for this user.
        org.junit.jupiter.api.Assertions.assertEquals(2, entitlementRepository.findAll().size());
    }

    @Test
    void mockSucceed_calledTwice_doesNotDuplicateEntitlements() throws Exception {
        Long paymentId = createOrderAndPayment("webhook-key-2");

        mockMvc.perform(post("/api/v1/payments/" + paymentId + "/mock/succeed")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/payments/" + paymentId + "/mock/succeed")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk());

        org.junit.jupiter.api.Assertions.assertEquals(2, entitlementRepository.findAll().size());
    }

    @Test
    void mockFail_marksOrderFailed_noEntitlements() throws Exception {
        Long paymentId = createOrderAndPayment("webhook-key-3");

        mockMvc.perform(post("/api/v1/payments/" + paymentId + "/mock/fail")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAYMENT_FAILED"));

        org.junit.jupiter.api.Assertions.assertTrue(entitlementRepository.findAll().isEmpty());
    }

    @Test
    void directWebhook_badSignature_returns401() throws Exception {
        String body = "{\"eventId\":\"" + UUID.randomUUID() + "\",\"type\":\"PAYMENT_SUCCEEDED\","
                + "\"paymentId\":1,\"providerReference\":\"mock_x\",\"amount\":100,\"occurredAt\":\"2026-01-01T00:00:00\"}";

        mockMvc.perform(post("/api/v1/payments/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Webhook-Signature", "not-a-real-signature")
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_WEBHOOK_SIGNATURE"));
    }

    @Test
    void directWebhook_sameEventIdTwice_secondIsNoOp() throws Exception {
        Long paymentId = createOrderAndPayment("webhook-key-4");
        String eventId = UUID.randomUUID().toString();
        String body = "{\"eventId\":\"" + eventId + "\",\"type\":\"PAYMENT_SUCCEEDED\","
                + "\"paymentId\":" + paymentId + ",\"providerReference\":\"mock_x\",\"amount\":9999.00,"
                + "\"occurredAt\":\"2026-01-01T00:00:00\"}";
        String signature = sign(body);

        mockMvc.perform(post("/api/v1/payments/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Webhook-Signature", signature)
                        .content(body))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/payments/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Webhook-Signature", signature)
                        .content(body))
                .andExpect(status().isOk());

        org.junit.jupiter.api.Assertions.assertEquals(1, paymentWebhookEventRepository.findAll().size());
        org.junit.jupiter.api.Assertions.assertEquals(2, entitlementRepository.findAll().size());
    }

    private String sign(String rawBody) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] digest = mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder();
        for (byte b : digest) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }
}
