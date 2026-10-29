package com.narturebelle.payment;

import com.narturebelle.model.Order;
import com.narturebelle.model.Payment;

/** The Phase 7 seam: a real gateway (Razorpay or similar) implements this exact interface alongside MockPaymentProvider. */
public interface PaymentProvider {

    CheckoutSession initiateCheckout(Payment payment, Order order);

    boolean verifySignature(String rawBody, String signatureHeader);

    WebhookEventPayload parseEvent(String rawBody);
}
