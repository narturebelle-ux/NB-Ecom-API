package com.narturebelle.money;

import com.narturebelle.internal.InternalTokenFilter;
import com.narturebelle.model.PlanCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;
import java.util.function.Supplier;

/** Private calls to NB-Ecom-Payments-API, which owns orders and payments. Used once money.base-url is set. */
@Component
@ConditionalOnExpression("!'${money.base-url:}'.isEmpty()")
public class HttpMoneyGateway implements MoneyGateway {

    private record OrderSummary(boolean hasAnyOrder, boolean hasPaidOrder, boolean hasPaidOrderAboveZero) {}

    private record RewardRequest(String rewardKey, Long userId, PlanCode planCode) {}

    private final RestClient restClient;

    @Autowired
    public HttpMoneyGateway(@Value("${money.base-url}") String baseUrl, @Value("${internal.api-token:}") String token) {
        this(client(RestClient.builder(), baseUrl, token));
    }

    HttpMoneyGateway(RestClient restClient) {
        this.restClient = restClient;
    }

    static RestClient client(RestClient.Builder builder, String baseUrl, String token) {
        SimpleClientHttpRequestFactory timeouts = new SimpleClientHttpRequestFactory();
        timeouts.setConnectTimeout(3000);
        timeouts.setReadTimeout(10000);
        return builder.requestFactory(timeouts)
                .baseUrl(baseUrl)
                .defaultHeader(InternalTokenFilter.HEADER, token)
                .build();
    }

    @Override
    public boolean hasAnyOrder(Long userId) {
        return orderSummary(userId).hasAnyOrder();
    }

    @Override
    public boolean hasPaidOrder(Long userId) {
        return orderSummary(userId).hasPaidOrder();
    }

    @Override
    public boolean hasPaidOrderAboveZero(Long userId) {
        return orderSummary(userId).hasPaidOrderAboveZero();
    }

    private OrderSummary orderSummary(Long userId) {
        return call(() -> restClient.get().uri("/internal/v1/users/{id}/order-summary", userId).retrieve().body(OrderSummary.class));
    }

    @Override
    public Map<String, Object> moneyExport(Long userId) {
        return call(() -> restClient.get().uri("/internal/v1/users/{id}/money-export", userId).retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {}));
    }

    @Override
    public RewardOrder grantReward(String rewardKey, Long userId, PlanCode planCode) {
        return call(() -> restClient.post().uri("/internal/v1/orders/reward")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new RewardRequest(rewardKey, userId, planCode))
                .retrieve().body(RewardOrder.class));
    }

    private static <T> T call(Supplier<T> request) {
        try {
            return request.get();
        } catch (RestClientException e) {
            throw new MoneyUnavailableException("NB-Ecom-Payments-API couldn't answer", e);
        }
    }
}
