package com.narturebelle.dto;

import com.narturebelle.model.InstallmentSchedule;
import com.narturebelle.model.Order;
import com.narturebelle.model.OrderStatus;
import com.narturebelle.model.PaymentMode;
import com.narturebelle.model.PlanCode;
import com.narturebelle.model.Refund;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class OrderResponse {

    private Long id;
    private Long planVersionId;
    private PlanCode planCode;
    private OrderStatus status;
    private PaymentMode paymentMode;
    private BigDecimal price;
    private List<InstallmentScheduleResponse> installmentSchedule;
    private List<RefundResponse> refunds;
    private LocalDateTime createdAt;

    public static OrderResponse from(Order order, PlanCode planCode, List<InstallmentSchedule> installmentSchedule,
                                      List<Refund> refunds) {
        OrderResponse dto = new OrderResponse();
        dto.id = order.getId();
        dto.planVersionId = order.getPlanVersionId();
        dto.planCode = planCode;
        dto.status = order.getStatus();
        dto.paymentMode = order.getPaymentMode();
        dto.price = order.getPrice();
        dto.installmentSchedule = installmentSchedule.stream().map(InstallmentScheduleResponse::from).toList();
        dto.refunds = refunds.stream().map(RefundResponse::from).toList();
        dto.createdAt = order.getCreatedAt();
        return dto;
    }

    public Long getId() { return id; }
    public Long getPlanVersionId() { return planVersionId; }
    public PlanCode getPlanCode() { return planCode; }
    public OrderStatus getStatus() { return status; }
    public PaymentMode getPaymentMode() { return paymentMode; }
    public BigDecimal getPrice() { return price; }
    public List<InstallmentScheduleResponse> getInstallmentSchedule() { return installmentSchedule; }
    public List<RefundResponse> getRefunds() { return refunds; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
