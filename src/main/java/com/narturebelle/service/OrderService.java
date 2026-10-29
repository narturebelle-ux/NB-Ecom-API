package com.narturebelle.service;

import com.narturebelle.dto.CreateOrderRequest;
import com.narturebelle.exception.IdempotencyKeyConflictException;
import com.narturebelle.exception.PlanNotFoundException;
import com.narturebelle.model.InstallmentSchedule;
import com.narturebelle.model.Order;
import com.narturebelle.model.OrderStatus;
import com.narturebelle.model.PaymentMode;
import com.narturebelle.model.PlanVersion;
import com.narturebelle.repository.InstallmentScheduleRepository;
import com.narturebelle.repository.OrderRepository;
import com.narturebelle.security.AuditService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class OrderService {

    private final OrderRepository orderRepository;
    private final InstallmentScheduleRepository installmentScheduleRepository;
    private final PlanService planService;
    private final AuditService auditService;

    public OrderService(OrderRepository orderRepository, InstallmentScheduleRepository installmentScheduleRepository,
                         PlanService planService, AuditService auditService) {
        this.orderRepository = orderRepository;
        this.installmentScheduleRepository = installmentScheduleRepository;
        this.planService = planService;
        this.auditService = auditService;
    }

    public Order createOrder(Long userId, CreateOrderRequest req, String idempotencyKey) {
        String fingerprint = fingerprint(userId, req.getPlanId(), req.getPaymentMode());

        Optional<Order> existing = orderRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
        if (existing.isPresent()) {
            if (existing.get().getRequestFingerprint().equals(fingerprint)) {
                return existing.get();
            }
            throw new IdempotencyKeyConflictException(
                    "This Idempotency-Key was already used for a different request.");
        }

        PlanVersion planVersion = planService.findActiveVersion(req.getPlanId())
                .orElseThrow(() -> new PlanNotFoundException("No active plan found for id " + req.getPlanId()));

        BigDecimal price = req.getPaymentMode() == PaymentMode.ONE_TIME
                ? planVersion.getOneTimePrice()
                : planVersion.getInstallmentTotalPrice();

        Order order = new Order();
        order.setUserId(userId);
        order.setPlanVersionId(planVersion.getId());
        order.setIdempotencyKey(idempotencyKey);
        order.setRequestFingerprint(fingerprint);
        order.setStatus(OrderStatus.PENDING);
        order.setPaymentMode(req.getPaymentMode());
        order.setPrice(price);

        Order saved;
        try {
            saved = orderRepository.save(order);
        } catch (DataIntegrityViolationException raceLoser) {
            // Lost a race to a concurrent duplicate request with the same key — the winner's row is
            // now visible; replay it instead of surfacing a 500.
            return orderRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey)
                    .orElseThrow(() -> raceLoser);
        }

        if (req.getPaymentMode() == PaymentMode.INSTALLMENT) {
            generateInstallmentSchedule(saved, planVersion);
        }

        auditService.record("ORDER_CREATED", "Order", String.valueOf(saved.getId()), null,
                Map.of("planVersionId", saved.getPlanVersionId(), "price", saved.getPrice().toString()));

        return saved;
    }

    public List<Order> listForUser(Long userId) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public Optional<Order> find(Long id) {
        return orderRepository.findById(id);
    }

    public List<InstallmentSchedule> installmentsFor(Long orderId) {
        return installmentScheduleRepository.findByOrderIdOrderByInstallmentNumberAsc(orderId);
    }

    /** Placeholder cadence: evenly split, 30 days apart, first installment due immediately. */
    private void generateInstallmentSchedule(Order order, PlanVersion planVersion) {
        int count = planVersion.getInstallmentCount();
        BigDecimal total = order.getPrice();
        BigDecimal perInstallment = total.divide(BigDecimal.valueOf(count), 2, RoundingMode.DOWN);
        BigDecimal allocated = BigDecimal.ZERO;

        for (int i = 1; i <= count; i++) {
            InstallmentSchedule installment = new InstallmentSchedule();
            installment.setOrderId(order.getId());
            installment.setInstallmentNumber(i);
            BigDecimal amount = (i == count) ? total.subtract(allocated) : perInstallment;
            installment.setAmount(amount);
            allocated = allocated.add(amount);
            installment.setDueDate(LocalDate.now().plusDays(30L * (i - 1)));
            installmentScheduleRepository.save(installment);
        }
    }

    private String fingerprint(Long userId, Long planId, PaymentMode paymentMode) {
        try {
            String raw = userId + "|" + planId + "|" + paymentMode;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
