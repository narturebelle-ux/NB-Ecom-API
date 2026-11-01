package com.narturebelle.service;

import com.narturebelle.internal.MoneyEventController.PaymentSucceeded;
import com.narturebelle.internal.MoneyEventController.RefundIssued;
import com.narturebelle.model.OrderStatus;
import com.narturebelle.model.PaidOrder;
import com.narturebelle.repository.PaidOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** What Care does when NB-Ecom-Payments-API reports a payment or refund. */
@Service
public class MoneyEventService {

    private final EntitlementService entitlementService;
    private final ReferralService referralService;
    private final PaidOrderRepository paidOrderRepository;
    private final TransactionTemplate transaction;

    public MoneyEventService(EntitlementService entitlementService, ReferralService referralService,
                             PaidOrderRepository paidOrderRepository, PlatformTransactionManager transactionManager) {
        this.entitlementService = entitlementService;
        this.referralService = referralService;
        this.paidOrderRepository = paidOrderRepository;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /**
     * Sessions and the paid record commit together. Referral work runs after, with no transaction held. If it fails,
     * this throws so the event is delivered again: the steps already done are safe to repeat.
     */
    public void onPaymentSucceeded(PaymentSucceeded event) {
        transaction.executeWithoutResult(status -> {
            entitlementService.grant(event.orderId(), event.userId(), event.planCode(), event.expiresAt(), event.sessions());
            if (event.price().signum() > 0 && !paidOrderRepository.existsById(event.orderId())) {
                paidOrderRepository.save(new PaidOrder(event.orderId(), event.userId()));
            }
        });
        referralService.onPaid(event.orderId(), event.userId(), event.price());
    }

    /** A refund doesn't change her sessions; it only ends the order's paid status, which paid votes count. */
    public void onRefundIssued(RefundIssued event) {
        if (event.orderStatus() != OrderStatus.PAID) {
            paidOrderRepository.markRefunded(event.orderId(), event.userId());
        }
    }
}
