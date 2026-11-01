package com.narturebelle.model;

import jakarta.persistence.*;

/**
 * Care's own record of which real purchases are PAID, so paid votes can be counted without reading orders. Free
 * reward orders never appear (F3-S2). A refund keeps the row and marks it, so a late event can't restore the vote.
 */
@Entity
@Table(name = "paid_orders")
public class PaidOrder {

    @Id
    private Long orderId;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private boolean refunded;

    public PaidOrder() {}

    public PaidOrder(Long orderId, Long userId) {
        this.orderId = orderId;
        this.userId = userId;
    }

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public boolean isRefunded() { return refunded; }
    public void setRefunded(boolean refunded) { this.refunded = refunded; }
}
