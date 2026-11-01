package com.narturebelle.money;

import com.narturebelle.model.PlanCode;
import com.narturebelle.model.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HttpMoneyGatewayTest {

    private MockRestServiceServer server;
    private HttpMoneyGateway gateway;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("http://money")
                .defaultHeader("X-Internal-Token", "secret");
        server = MockRestServiceServer.bindTo(builder).build();
        gateway = new HttpMoneyGateway(builder.build());
    }

    @Test
    void orderSummary_answersEachQuestion() {
        String summary = "{\"hasAnyOrder\":true,\"hasPaidOrder\":false,\"hasPaidOrderAboveZero\":false}";
        server.expect(requestTo("http://money/internal/v1/users/7/order-summary"))
                .andExpect(header("X-Internal-Token", "secret"))
                .andRespond(withSuccess(summary, MediaType.APPLICATION_JSON));

        assertTrue(gateway.hasAnyOrder(7L));
    }

    @Test
    void grantReward_sendsTheKey_andReadsTheOrder() {
        server.expect(requestTo("http://money/internal/v1/orders/reward"))
                .andExpect(jsonPath("$.rewardKey").value("referral-reward-7"))
                .andExpect(jsonPath("$.planCode").value("PREMIUM"))
                .andRespond(withSuccess("""
                        {"orderId":42,"planVersionId":3,"planCode":"PREMIUM","expiresAt":"2027-11-01T10:15:30.123456",
                         "sessions":[{"professionalType":"GYNECOLOGIST","sessionCount":6}]}
                        """, MediaType.APPLICATION_JSON));

        MoneyGateway.RewardOrder reward = gateway.grantReward("referral-reward-7", 7L, PlanCode.PREMIUM);

        assertEquals(42L, reward.orderId());
        assertEquals(LocalDateTime.parse("2027-11-01T10:15:30.123456"), reward.expiresAt());
        assertEquals(List.of(new MoneyGateway.SessionGrant(Role.GYNECOLOGIST, 6)), reward.sessions());
    }

    @Test
    void outage_isUnavailable_neverNotPaid() {
        server.expect(requestTo("http://money/internal/v1/users/7/order-summary"))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        assertThrows(MoneyUnavailableException.class, () -> gateway.hasPaidOrder(7L));
    }
}
