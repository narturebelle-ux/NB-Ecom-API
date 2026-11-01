package com.narturebelle.money;

import com.narturebelle.model.Order;
import com.narturebelle.model.OrderStatus;
import com.narturebelle.model.PaymentMode;
import com.narturebelle.model.Plan;
import com.narturebelle.model.PlanCode;
import com.narturebelle.model.PlanVersion;
import com.narturebelle.repository.OrderRepository;
import com.narturebelle.repository.PaymentRepository;
import com.narturebelle.repository.PlanRepository;
import com.narturebelle.repository.PlanVersionRepository;
import com.narturebelle.service.PlanPricing;
import com.narturebelle.service.PlanService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Today's behaviour, from this API's own tables. Removed with the rest of the money code after cut-over. */
@Component
@ConditionalOnExpression("'${money.base-url:}'.isEmpty()")
public class LocalMoneyGateway implements MoneyGateway {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final PlanRepository planRepository;
    private final PlanVersionRepository planVersionRepository;
    private final PlanService planService;

    public LocalMoneyGateway(OrderRepository orderRepository, PaymentRepository paymentRepository,
                             PlanRepository planRepository, PlanVersionRepository planVersionRepository,
                             PlanService planService) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.planRepository = planRepository;
        this.planVersionRepository = planVersionRepository;
        this.planService = planService;
    }

    @Override
    public boolean hasAnyOrder(Long userId) {
        return orderRepository.existsByUserId(userId);
    }

    @Override
    public boolean hasPaidOrder(Long userId) {
        return orderRepository.existsByUserIdAndStatus(userId, OrderStatus.PAID);
    }

    @Override
    public boolean hasPaidOrderAboveZero(Long userId) {
        return orderRepository.existsByUserIdAndStatusAndPriceGreaterThan(userId, OrderStatus.PAID, BigDecimal.ZERO);
    }

    @Override
    public Map<String, Object> moneyExport(Long userId) {
        List<Order> orders = orderRepository.findByUserIdOrderByCreatedAtDesc(userId);
        Map<String, Object> export = new LinkedHashMap<>();
        export.put("orders", orders);
        export.put("payments", orders.stream()
                .flatMap(o -> paymentRepository.findByOrderIdOrderByCreatedAtDesc(o.getId()).stream()).toList());
        return export;
    }

    /** Its own transaction: callers may be in an after-commit step, where a plain save would never be written. */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RewardOrder grantReward(String rewardKey, Long userId, PlanCode planCode) {
        Plan plan = planRepository.findByCode(planCode)
                .orElseThrow(() -> new IllegalStateException("No " + planCode + " plan to grant as a reward"));
        PlanVersion version = planVersionRepository.findByPlanIdAndActiveTrue(plan.getId())
                .orElseThrow(() -> new IllegalStateException(planCode + " plan has no active version"));

        Order saved = orderRepository.findByUserIdAndIdempotencyKey(userId, rewardKey).orElseGet(() -> {
            Order order = new Order();
            order.setUserId(userId);
            order.setPlanVersionId(version.getId());
            order.setIdempotencyKey(rewardKey);
            order.setRequestFingerprint("referral-reward");
            order.setStatus(OrderStatus.PAID);
            order.setPaymentMode(PaymentMode.ONE_TIME);
            order.setPrice(BigDecimal.ZERO);
            order.setMonths(9); // the full plan, not priced from a due date
            return orderRepository.save(order);
        });

        List<SessionGrant> sessions = planService.allocationsFor(version.getId()).stream()
                .map(allocation -> new SessionGrant(allocation.getProfessionalType(),
                        PlanPricing.prorateSessions(allocation.getSessionCount(), saved.effectiveMonths())))
                .toList();
        return new RewardOrder(saved.getId(), planCode, LocalDateTime.now().plusDays(version.getValidityDays()), sessions);
    }
}
