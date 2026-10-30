package com.narturebelle.controller;

import com.narturebelle.model.Role;
import com.narturebelle.model.User;
import com.narturebelle.repository.EntitlementRepository;
import com.narturebelle.repository.OrderRepository;
import com.narturebelle.repository.PaymentRepository;
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

/** Covers 2.6: GET /entitlements — the session wallet. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EntitlementTest {

    private static final String TOKEN = "entitlement-token";
    private static final String TOKEN_NO_ORDERS = "entitlement-token-no-orders";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private EntitlementRepository entitlementRepository;

    @MockBean
    private FirebaseTokenVerifier firebaseTokenVerifier;

    @BeforeEach
    void setUp() throws Exception {
        entitlementRepository.deleteAll();
        paymentRepository.deleteAll();
        orderRepository.deleteAll();
        userRepository.deleteAll();

        User user = new User();
        user.setFirebaseUid("uid-entitlement");
        user.setEmail("entitlement@example.com");
        user.setEmailVerified(true);
        user.setRoles(Set.of(Role.MOTHER));
        userRepository.save(user);

        User noOrdersUser = new User();
        noOrdersUser.setFirebaseUid("uid-entitlement-noorders");
        noOrdersUser.setEmail("entitlement-noorders@example.com");
        noOrdersUser.setEmailVerified(true);
        noOrdersUser.setRoles(Set.of(Role.MOTHER));
        userRepository.save(noOrdersUser);

        when(firebaseTokenVerifier.verify(TOKEN))
                .thenReturn(new VerifiedFirebaseToken("uid-entitlement", "entitlement@example.com", true, null, null));
        when(firebaseTokenVerifier.verify(TOKEN_NO_ORDERS))
                .thenReturn(new VerifiedFirebaseToken("uid-entitlement-noorders", "entitlement-noorders@example.com", true, null, null));
    }

    @Test
    void noOrders_returnsEmptyList_notError() throws Exception {
        mockMvc.perform(get("/api/v1/entitlements").header("Authorization", "Bearer " + TOKEN_NO_ORDERS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void afterPaidOrder_walletMatchesPlanAllocation() throws Exception {
        String orderBody = mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + TOKEN)
                        .header("Idempotency-Key", "entitlement-key-1")
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
        Long paymentId = Long.valueOf(paymentBody.replaceAll(".*\"id\":(\\d+).*", "$1"));

        mockMvc.perform(post("/api/v1/payments/" + paymentId + "/mock/succeed")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/entitlements").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(2)))
                .andExpect(jsonPath("$[*].professionalType",
                        org.hamcrest.Matchers.containsInAnyOrder("GYNECOLOGIST", "NUTRITIONIST")))
                .andExpect(jsonPath("$[0].usedSessions").value(0))
                .andExpect(jsonPath("$[0].planCode").value("STANDARD"));
    }
}
