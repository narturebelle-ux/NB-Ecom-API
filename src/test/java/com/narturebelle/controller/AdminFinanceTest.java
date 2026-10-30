package com.narturebelle.controller;

import com.narturebelle.model.Role;
import com.narturebelle.model.User;
import com.narturebelle.repository.EntitlementRepository;
import com.narturebelle.repository.OrderRepository;
import com.narturebelle.repository.PaymentRepository;
import com.narturebelle.repository.RefundRepository;
import com.narturebelle.repository.UserRepository;
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

import java.util.Set;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Covers 2.7/2.8: admin finance summary and admin-entered refunds. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminFinanceTest {

    private static final String TOKEN_USER = "finance-token-user";
    private static final String TOKEN_ADMIN = "finance-token-admin";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private RefundRepository refundRepository;

    @Autowired
    private EntitlementRepository entitlementRepository;

    @MockBean
    private FirebaseTokenVerifier firebaseTokenVerifier;

    private Long paidOrderId;

    @BeforeEach
    void setUp() throws Exception {
        entitlementRepository.deleteAll();
        paymentRepository.deleteAll();
        refundRepository.deleteAll();
        orderRepository.deleteAll();
        userRepository.deleteAll();

        User user = new User();
        user.setFirebaseUid("uid-finance-user");
        user.setEmail("finance-user@example.com");
        user.setEmailVerified(true);
        user.setRoles(Set.of(Role.MOTHER));
        userRepository.save(user);

        User admin = new User();
        admin.setFirebaseUid("uid-finance-admin");
        admin.setEmail("finance-admin@example.com");
        admin.setEmailVerified(true);
        admin.setRoles(Set.of(Role.FINANCE_ADMIN));
        userRepository.save(admin);

        when(firebaseTokenVerifier.verify(TOKEN_USER))
                .thenReturn(new VerifiedFirebaseToken("uid-finance-user", "finance-user@example.com", true, null, null));
        when(firebaseTokenVerifier.verify(TOKEN_ADMIN))
                .thenReturn(new VerifiedFirebaseToken("uid-finance-admin", "finance-admin@example.com", true, null, null));

        String orderBody = mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + TOKEN_USER)
                        .header("Idempotency-Key", "finance-key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planId\":1,\"paymentMode\":\"ONE_TIME\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        paidOrderId = Long.valueOf(orderBody.replaceAll(".*\"id\":(\\d+).*", "$1"));

        String paymentBody = mockMvc.perform(post("/api/v1/payments/create")
                        .header("Authorization", "Bearer " + TOKEN_USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":" + paidOrderId + "}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Long paymentId = Long.valueOf(paymentBody.replaceAll(".*\"id\":(\\d+).*", "$1"));

        mockMvc.perform(post("/api/v1/payments/" + paymentId + "/mock/succeed")
                        .header("Authorization", "Bearer " + TOKEN_USER))
                .andExpect(status().isOk());
    }

    @Test
    void nonAdmin_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/finance/summary").header("Authorization", "Bearer " + TOKEN_USER))
                .andExpect(status().isForbidden());
    }

    @Test
    void admin_seesCorrectSummaryForOnePaidOrder() throws Exception {
        mockMvc.perform(get("/api/v1/admin/finance/summary").header("Authorization", "Bearer " + TOKEN_ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paidOrderCount").value(1))
                .andExpect(jsonPath("$.grossRevenue").value(9999.00))
                .andExpect(jsonPath("$.totalRefunded").value(0))
                .andExpect(jsonPath("$.netRevenue").value(9999.00))
                .andExpect(jsonPath("$.deferredRevenueLiability").value(9999.00));
    }

    @Test
    void partialRefund_updatesOrderStatusAndSummary() throws Exception {
        mockMvc.perform(post("/api/v1/admin/orders/" + paidOrderId + "/refunds")
                        .header("Authorization", "Bearer " + TOKEN_ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":3000.00,\"reason\":\"Partial refund per customer request\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(3000.00));

        mockMvc.perform(get("/api/v1/admin/finance/summary").header("Authorization", "Bearer " + TOKEN_ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRefunded").value(3000.00))
                .andExpect(jsonPath("$.netRevenue").value(6999.00));

        mockMvc.perform(get("/api/v1/orders/" + paidOrderId).header("Authorization", "Bearer " + TOKEN_USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PARTIALLY_REFUNDED"));
    }

    @Test
    void refundExceedingRemaining_returns409() throws Exception {
        mockMvc.perform(post("/api/v1/admin/orders/" + paidOrderId + "/refunds")
                        .header("Authorization", "Bearer " + TOKEN_ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":99999.00,\"reason\":\"Too much\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REFUND_NOT_ALLOWED"));
    }

    @Test
    void refundOnPendingOrder_returns409() throws Exception {
        // Saved directly: she already owns a paid plan here, so POST /orders would refuse a second one.
        com.narturebelle.model.Order paid = orderRepository.findById(paidOrderId).orElseThrow();
        com.narturebelle.model.Order pending = new com.narturebelle.model.Order();
        pending.setUserId(paid.getUserId());
        pending.setPlanVersionId(paid.getPlanVersionId());
        pending.setIdempotencyKey("finance-key-pending");
        pending.setRequestFingerprint("fp");
        pending.setStatus(com.narturebelle.model.OrderStatus.PENDING);
        pending.setPaymentMode(paid.getPaymentMode());
        pending.setPrice(paid.getPrice());
        Long pendingOrderId = orderRepository.save(pending).getId();

        mockMvc.perform(post("/api/v1/admin/orders/" + pendingOrderId + "/refunds")
                        .header("Authorization", "Bearer " + TOKEN_ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":100.00,\"reason\":\"Never paid\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REFUND_NOT_ALLOWED"));
    }
}
