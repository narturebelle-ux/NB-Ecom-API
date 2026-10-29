package com.narturebelle.dto;

import com.narturebelle.model.Payout;
import com.narturebelle.model.PayoutStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class PayoutResponse {

    private Long id;
    private Long bookingId;
    private Long providerId;
    private BigDecimal amount;
    private PayoutStatus status;
    private LocalDateTime createdAt;

    public static PayoutResponse from(Payout payout) {
        PayoutResponse dto = new PayoutResponse();
        dto.id = payout.getId();
        dto.bookingId = payout.getBookingId();
        dto.providerId = payout.getProviderId();
        dto.amount = payout.getAmount();
        dto.status = payout.getStatus();
        dto.createdAt = payout.getCreatedAt();
        return dto;
    }

    public Long getId() { return id; }
    public Long getBookingId() { return bookingId; }
    public Long getProviderId() { return providerId; }
    public BigDecimal getAmount() { return amount; }
    public PayoutStatus getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
