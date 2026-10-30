package com.narturebelle.service;

import com.narturebelle.exception.InvalidWebhookSignatureException;
import com.narturebelle.exception.PaymentNotAllowedException;
import com.narturebelle.exception.PaymentNotFoundException;
import com.narturebelle.model.Order;
import com.narturebelle.model.OrderStatus;
import com.narturebelle.model.Payment;
import com.narturebelle.model.PaymentProviderType;
import com.narturebelle.model.PaymentStatus;
import com.narturebelle.model.PaymentWebhookEvent;
import com.narturebelle.model.WebhookEventType;
import com.narturebelle.payment.CheckoutSession;
import com.narturebelle.payment.PaymentProvider;
import com.narturebelle.payment.WebhookEventPayload;
import com.narturebelle.repository.OrderRepository;
import com.narturebelle.repository.PaymentRepository;
import com.narturebelle.repository.PaymentWebhookEventRepository;
import com.narturebelle.security.AuditService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentWebhookEventRepository paymentWebhookEventRepository;
    private final OrderRepository orderRepository;
    private final PaymentProvider paymentProvider;
    private final EntitlementService entitlementService;
    private final AuditService auditService;
    private final ReferralService referralService;

    public PaymentService(PaymentRepository paymentRepository, PaymentWebhookEventRepository paymentWebhookEventRepository,
                           OrderRepository orderRepository, PaymentProvider paymentProvider,
                           EntitlementService entitlementService, AuditService auditService,
                           ReferralService referralService) {
        this.paymentRepository = paymentRepository;
        this.paymentWebhookEventRepository = paymentWebhookEventRepository;
        this.orderRepository = orderRepository;
        this.paymentProvider = paymentProvider;
        this.entitlementService = entitlementService;
        this.auditService = auditService;
        this.referralService = referralService;
    }

    public Payment createPayment(Order order) {
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new PaymentNotAllowedException("Order is not payable in its current status: " + order.getStatus());
        }

        List<Payment> existing = paymentRepository.findByOrderIdOrderByCreatedAtDesc(order.getId());
        Optional<Payment> stillCreated = existing.stream()
                .filter(p -> p.getStatus() == PaymentStatus.CREATED)
                .findFirst();
        if (stillCreated.isPresent()) {
            return stillCreated.get();
        }

        Payment payment = new Payment();
        payment.setOrderId(order.getId());
        payment.setProvider(PaymentProviderType.MOCK);
        payment.setStatus(PaymentStatus.CREATED);
        payment.setAmount(order.getPrice());

        CheckoutSession session = paymentProvider.initiateCheckout(payment, order);
        payment.setProviderPaymentReference(session.providerPaymentReference());
        Payment saved = paymentRepository.save(payment);

        auditService.record("PAYMENT_CREATED", "Payment", String.valueOf(saved.getId()), null,
                Map.of("orderId", order.getId(), "amount", saved.getAmount().toString()));

        return saved;
    }

    @Transactional
    public void processWebhook(String rawBody, String signatureHeader) {
        if (!paymentProvider.verifySignature(rawBody, signatureHeader)) {
            throw new InvalidWebhookSignatureException("Webhook signature verification failed.");
        }

        WebhookEventPayload event = paymentProvider.parseEvent(rawBody);

        if (paymentWebhookEventRepository.existsByProviderEventId(event.eventId())) {
            return; // already processed -- idempotent no-op
        }

        PaymentWebhookEvent ledgerRow = new PaymentWebhookEvent();
        ledgerRow.setPaymentId(event.paymentId());
        ledgerRow.setProviderEventId(event.eventId());
        ledgerRow.setEventType(event.type().name());
        ledgerRow.setRawPayload(rawBody);
        try {
            paymentWebhookEventRepository.save(ledgerRow);
        } catch (DataIntegrityViolationException alreadyDelivered) {
            return; // lost a race to a concurrent delivery of the identical event
        }

        Payment payment = paymentRepository.findById(event.paymentId())
                .orElseThrow(() -> new PaymentNotFoundException("Unknown payment: " + event.paymentId()));
        Order order = orderRepository.findById(payment.getOrderId())
                .orElseThrow(() -> new IllegalStateException("Payment references a missing order: " + payment.getOrderId()));

        if (event.type() == WebhookEventType.PAYMENT_SUCCEEDED) {
            handleSucceeded(payment, order);
        } else {
            handleFailed(payment, order);
        }
    }

    private void handleSucceeded(Payment payment, Order order) {
        if (payment.getStatus() != PaymentStatus.SUCCEEDED) {
            payment.setStatus(PaymentStatus.SUCCEEDED);
            paymentRepository.save(payment);
            auditService.record("PAYMENT_SUCCEEDED", "Payment", String.valueOf(payment.getId()), null);
        }
        if (order.getStatus() == OrderStatus.PENDING) {
            order.setStatus(OrderStatus.PAID);
            orderRepository.save(order);
            auditService.record("ORDER_PAID", "Order", String.valueOf(order.getId()), null);
            // Referral credit runs after this webhook commits, in its own transaction: a referral problem
            // must never roll back a payment.
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        referralService.onOrderPaid(order);
                    }
                });
            } else {
                referralService.onOrderPaid(order);
            }
        }
        if (order.getStatus() == OrderStatus.FAILED) {
            // Replaced by a newer checkout, or already failed: money arrived for an order that's closed. Flag it for
            // a person to refund rather than granting a second plan.
            auditService.record("PAYMENT_ON_CLOSED_ORDER", "Order", String.valueOf(order.getId()), "Payment succeeded on a closed order");
            return;
        }
        entitlementService.grantForOrder(order);
    }

    private void handleFailed(Payment payment, Order order) {
        if (payment.getStatus() != PaymentStatus.FAILED) {
            payment.setStatus(PaymentStatus.FAILED);
            paymentRepository.save(payment);
            auditService.record("PAYMENT_FAILED", "Payment", String.valueOf(payment.getId()), null);
        }
        if (order.getStatus() == OrderStatus.PENDING) {
            order.setStatus(OrderStatus.FAILED);
            orderRepository.save(order);
            auditService.record("ORDER_FAILED", "Order", String.valueOf(order.getId()), null);
        }
    }

    public Optional<Payment> find(Long id) {
        return paymentRepository.findById(id);
    }
}
