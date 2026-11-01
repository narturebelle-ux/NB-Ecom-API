package com.narturebelle.controller;

import com.narturebelle.model.PlanCode;
import com.narturebelle.model.Referral;
import com.narturebelle.model.Role;
import com.narturebelle.money.MoneyGateway;
import com.narturebelle.money.MoneyUnavailableException;
import com.narturebelle.repository.EntitlementRepository;
import com.narturebelle.repository.PaidOrderRepository;
import com.narturebelle.repository.ReferralRepository;
import com.narturebelle.repository.ReferralRewardRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A friend's payment earns the referrer's reward while the Money API is down: nothing may be lost. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "internal.api-token=test-internal-token")
class MoneyEventRewardTest {

    private static final long REFERRER = 7001L;
    private static final long BUYER = 7100L;
    private static final long ORDER = 9100L;
    private static final long REWARD_ORDER = 9200L;
    private static final String KEY = "referral-reward-" + REFERRER;
    private static final MoneyGateway.RewardOrder REWARD = new MoneyGateway.RewardOrder(REWARD_ORDER, PlanCode.PREMIUM,
            LocalDateTime.parse("2027-11-01T10:15:30.123456"), List.of(new MoneyGateway.SessionGrant(Role.GYNECOLOGIST, 6)));

    @Autowired private MockMvc mockMvc;
    @Autowired private ReferralRepository referralRepository;
    @Autowired private ReferralRewardRepository referralRewardRepository;
    @Autowired private EntitlementRepository entitlementRepository;
    @Autowired private PaidOrderRepository paidOrderRepository;

    @MockBean private MoneyGateway moneyGateway;

    @BeforeEach
    void fourFriendsAlreadyQualified() {
        entitlementRepository.deleteAll();
        paidOrderRepository.deleteAll();
        // Not deleteAll(): ReferralReward always reports itself as new, which makes that skip every row.
        referralRewardRepository.deleteAllInBatch();
        referralRepository.deleteAll();
        for (long friend = 7000; friend < 7004; friend++) {
            Referral referral = new Referral(REFERRER, friend + 1000);
            referral.setQualifiedAt(LocalDateTime.now());
            referralRepository.save(referral);
        }
        referralRepository.save(new Referral(REFERRER, BUYER));
    }

    private org.springframework.test.web.servlet.ResultActions deliver() throws Exception {
        String body = """
                {"eventId":"payment-succeeded:%d","schemaVersion":1,"orderId":%d,"userId":%d,"planCode":"STANDARD",
                 "months":9,"price":9999.00,"paidAt":"2026-11-01T10:15:30.123456","expiresAt":"2027-11-01T10:15:30.123456",
                 "sessions":[{"professionalType":"NUTRITIONIST","sessionCount":4}]}
                """.formatted(ORDER, ORDER, BUYER);
        return mockMvc.perform(post("/internal/v1/money-events/payment-succeeded").header("X-Internal-Token", "test-internal-token")
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void moneyOutage_failsTheEvent_andTheRetryFinishesTheReward() throws Exception {
        when(moneyGateway.grantReward(KEY, REFERRER, PlanCode.PREMIUM))
                .thenThrow(new MoneyUnavailableException("down", null))
                .thenReturn(REWARD);

        deliver().andExpect(status().isServiceUnavailable());

        // The buyer's own sessions and the friend's qualification stand; only the reward waits.
        assertEquals(1, entitlementRepository.findByUserIdOrderByCreatedAtDesc(BUYER).size());
        assertNotNull(referralRepository.findByReferredUserId(BUYER).orElseThrow().getQualifiedAt());
        assertFalse(referralRewardRepository.existsById(REFERRER));

        deliver().andExpect(status().isOk());

        assertEquals(REWARD_ORDER, referralRewardRepository.findById(REFERRER).orElseThrow().getOrderId());
        assertEquals(1, entitlementRepository.findByUserIdOrderByCreatedAtDesc(REFERRER).size());
        assertEquals(1, entitlementRepository.findByUserIdOrderByCreatedAtDesc(BUYER).size());

        deliver().andExpect(status().isOk());
        verify(moneyGateway, times(2)).grantReward(any(), any(), any());
        assertFalse(paidOrderRepository.existsById(REWARD_ORDER));
    }

    @Test
    void moneyIsCalledWithNoTransactionOpen() throws Exception {
        AtomicBoolean inTransaction = new AtomicBoolean(true);
        when(moneyGateway.grantReward(KEY, REFERRER, PlanCode.PREMIUM)).thenAnswer(call -> {
            inTransaction.set(TransactionSynchronizationManager.isActualTransactionActive());
            return REWARD;
        });

        deliver().andExpect(status().isOk());

        assertFalse(inTransaction.get());
    }
}
