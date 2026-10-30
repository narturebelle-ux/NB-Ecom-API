package com.narturebelle.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.narturebelle.email.EmailMessage;
import com.narturebelle.email.EmailSender;
import com.narturebelle.model.*;
import com.narturebelle.repository.*;
import com.narturebelle.security.FirebaseTokenVerifier;
import com.narturebelle.security.VerifiedFirebaseToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Covers referral links: claiming rules, and the free Premium plan after 5 referred friends buy a plan. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReferralTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private ConsentRepository consentRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private PaymentWebhookEventRepository paymentWebhookEventRepository;
    @Autowired private InstallmentScheduleRepository installmentScheduleRepository;
    @Autowired private EntitlementRepository entitlementRepository;
    @Autowired private ReferralCodeRepository referralCodeRepository;
    @Autowired private ReferralRepository referralRepository;
    @Autowired private ReferralRewardRepository referralRewardRepository;
    @Autowired private PlanVersionRepository planVersionRepository;
    @Autowired private PlanRepository planRepository;

    @MockBean private FirebaseTokenVerifier firebaseTokenVerifier;
    @MockBean private EmailSender emailSender;

    private Long sharerId;

    @BeforeEach
    void setUp() throws Exception {
        referralRewardRepository.deleteAll();
        referralRepository.deleteAll();
        referralCodeRepository.deleteAll();
        entitlementRepository.deleteAll();
        installmentScheduleRepository.deleteAll();
        paymentWebhookEventRepository.deleteAll();
        paymentRepository.deleteAll();
        orderRepository.deleteAll();
        consentRepository.deleteAll();
        userRepository.deleteAll();
        sharerId = member("sharer", "Asha", "Rao");
    }

    private Long member(String name, String first, String last) throws Exception {
        User user = new User();
        user.setFirebaseUid("uid-ref-" + name);
        user.setEmail(name + "@example.com");
        user.setEmailVerified(true);
        user.setFirstName(first);
        user.setLastName(last);
        user.setRoles(Set.of(Role.MOTHER));
        Long id = userRepository.save(user).getId();
        for (ConsentDocumentType type : Set.of(ConsentDocumentType.TERMS, ConsentDocumentType.PRIVACY)) {
            Consent consent = new Consent();
            consent.setUserId(id);
            consent.setDocumentType(type);
            consent.setDocumentVersion(1);
            consentRepository.save(consent);
        }
        when(firebaseTokenVerifier.verify("tok-" + name))
                .thenReturn(new VerifiedFirebaseToken("uid-ref-" + name, name + "@example.com", true, null, null));
        return id;
    }

    private static String bearer(String name) {
        return "Bearer tok-" + name;
    }

    private String sharerCode() throws Exception {
        String body = mockMvc.perform(get("/api/v1/me/referral").header("Authorization", bearer("sharer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.target").value(5))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body).get("code").asText();
    }

    /** The real checkout: order, payment, mock-succeed webhook -- the same path a paying friend takes. */
    private void buyStandardPlan(String name) throws Exception {
        String order = mockMvc.perform(post("/api/v1/orders").header("Authorization", bearer(name))
                        .header("Idempotency-Key", "ref-" + name).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planId\":1,\"paymentMode\":\"ONE_TIME\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long orderId = objectMapper.readTree(order).get("id").asLong();
        String payment = mockMvc.perform(post("/api/v1/payments/create").header("Authorization", bearer(name))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"orderId\":" + orderId + "}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long paymentId = objectMapper.readTree(payment).get("id").asLong();
        mockMvc.perform(post("/api/v1/payments/" + paymentId + "/mock/succeed").header("Authorization", bearer(name)))
                .andExpect(status().isOk());
    }

    private void claim(String name, String code) throws Exception {
        mockMvc.perform(post("/api/v1/me/referral/claim").header("Authorization", bearer(name))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"" + code.toLowerCase() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.referrerName").value("Asha R."));
    }

    @Test
    void fivePayingFriends_earnTheSharerAFreePremiumPlan_once() throws Exception {
        String code = sharerCode();
        assertEquals(code, sharerCode()); // stable

        mockMvc.perform(get("/api/v1/referrals/" + code))
                .andExpect(status().isOk()).andExpect(jsonPath("$.referrerName").value("Asha R."));

        for (int i = 1; i <= 6; i++) {
            member("friend" + i, "Friend" + i, null);
            claim("friend" + i, code);
        }
        for (int i = 1; i <= 4; i++) buyStandardPlan("friend" + i);

        mockMvc.perform(get("/api/v1/me/referral").header("Authorization", bearer("sharer")))
                .andExpect(jsonPath("$.invited").value(6))
                .andExpect(jsonPath("$.qualified").value(4))
                .andExpect(jsonPath("$.rewardGranted").value(false));
        assertTrue(referralRewardRepository.findAll().isEmpty());

        buyStandardPlan("friend5");
        buyStandardPlan("friend6"); // a sixth friend doesn't grant a second plan

        mockMvc.perform(get("/api/v1/me/referral").header("Authorization", bearer("sharer")))
                .andExpect(jsonPath("$.qualified").value(6))
                .andExpect(jsonPath("$.rewardGranted").value(true))
                .andExpect(jsonPath("$.friends[0].name").value(org.hamcrest.Matchers.startsWith("Friend")))
                .andExpect(jsonPath("$.friends[0].purchased").value(true));

        List<Order> sharerOrders = orderRepository.findByUserIdOrderByCreatedAtDesc(sharerId);
        assertEquals(1, sharerOrders.size());
        Order reward = sharerOrders.get(0);
        assertEquals(OrderStatus.PAID, reward.getStatus());
        assertEquals(0, BigDecimal.ZERO.compareTo(reward.getPrice()));
        PlanVersion premium = planVersionRepository.findById(reward.getPlanVersionId()).orElseThrow();
        assertEquals(PlanCode.PREMIUM, planRepository.findById(premium.getPlanId()).orElseThrow().getCode());
        assertFalse(entitlementRepository.findByUserIdOrderByCreatedAtDesc(sharerId).isEmpty());

        ArgumentCaptor<EmailMessage> sent = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailSender, times(1)).send(sent.capture());
        assertEquals("sharer@example.com", sent.getValue().to());
    }

    @Test
    void claimRules() throws Exception {
        String code = sharerCode();
        member("friend", "Divya", "S");

        mockMvc.perform(post("/api/v1/me/referral/claim").header("Authorization", bearer("sharer"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"" + code + "\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("REFERRAL_SELF"));
        mockMvc.perform(post("/api/v1/me/referral/claim").header("Authorization", bearer("friend"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"NOPE1234\"}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("REFERRAL_CODE_NOT_FOUND"));
        mockMvc.perform(get("/api/v1/referrals/NOPE1234")).andExpect(status().isNotFound());

        claim("friend", code);
        mockMvc.perform(post("/api/v1/me/referral/claim").header("Authorization", bearer("friend"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"" + code + "\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("REFERRAL_ALREADY_CLAIMED"));

        // Someone who has already bought a plan isn't a new customer.
        member("customer", "Meera", "K");
        buyStandardPlan("customer");
        mockMvc.perform(post("/api/v1/me/referral/claim").header("Authorization", bearer("customer"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"" + code + "\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("REFERRAL_NOT_NEW_CUSTOMER"));

        mockMvc.perform(get("/api/v1/me/referral")).andExpect(status().isUnauthorized());
    }
}
