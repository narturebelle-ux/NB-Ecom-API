package com.narturebelle.internal;

import com.narturebelle.model.OrderStatus;
import com.narturebelle.model.PlanCode;
import com.narturebelle.money.MoneyGateway.SessionGrant;
import com.narturebelle.service.MoneyEventService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Events from NB-Ecom-Payments-API. Guarded by InternalTokenFilter. Delivery can repeat, so a repeated
 * event succeeds and changes nothing.
 */
@RestController
@RequestMapping("/internal/v1/money-events")
public class MoneyEventController {

    public record PaymentSucceeded(
            @NotBlank String eventId,
            @Min(1) @Max(1) int schemaVersion,
            @NotNull Long orderId,
            @NotNull Long userId,
            @NotNull PlanCode planCode,
            int months,
            @NotNull BigDecimal price,
            @NotNull LocalDateTime paidAt,
            @NotNull LocalDateTime expiresAt,
            @NotNull List<SessionGrant> sessions
    ) {}

    public record RefundIssued(
            @NotBlank String eventId,
            @Min(1) @Max(1) int schemaVersion,
            @NotNull Long refundId,
            @NotNull Long orderId,
            @NotNull Long userId,
            @NotNull BigDecimal amount,
            @NotNull OrderStatus orderStatus
    ) {}

    private final MoneyEventService moneyEventService;

    public MoneyEventController(MoneyEventService moneyEventService) {
        this.moneyEventService = moneyEventService;
    }

    @PostMapping("/payment-succeeded")
    public Map<String, Boolean> paymentSucceeded(@Valid @RequestBody PaymentSucceeded event) {
        moneyEventService.onPaymentSucceeded(event);
        return Map.of("received", true);
    }

    @PostMapping("/refund-issued")
    public Map<String, Boolean> refundIssued(@Valid @RequestBody RefundIssued event) {
        moneyEventService.onRefundIssued(event);
        return Map.of("received", true);
    }
}
