package com.narturebelle.dto;

import jakarta.validation.constraints.NotNull;

public class CreatePaymentRequest {

    @NotNull
    private Long orderId;

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
}
