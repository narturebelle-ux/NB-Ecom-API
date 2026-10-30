package com.narturebelle.service;

import com.narturebelle.model.Journey;
import com.narturebelle.model.JourneyStatus;
import com.narturebelle.repository.JourneyRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Duration-based plan pricing (product-owner decision, 2026-10-30). A plan's listed price covers the 9 months
 * of pregnancy, so its monthly rate is price / 9. She pays for the whole months left until her due date
 * (rounded up, 1-9), and her sessions shrink in the same proportion (at least 1 per professional the plan
 * includes). Without a due date to count from -- no active journey, no EDD, or already postpartum -- she's
 * quoted the full 9-month plan.
 */
@Component
public class PlanPricing {

    public static final int FULL_MONTHS = 9;
    private static final double AVERAGE_DAYS_PER_MONTH = 30.4375;

    private final JourneyRepository journeyRepository;

    public PlanPricing(JourneyRepository journeyRepository) {
        this.journeyRepository = journeyRepository;
    }

    /** dueDate is the EDD the months were counted from (null when quoted the full plan). */
    public record Duration(int months, LocalDate dueDate) {
        public boolean fromDueDate() { return dueDate != null; }
    }

    public Duration durationFor(Long userId) {
        Journey journey = journeyRepository.findByUserIdAndStatusNot(userId, JourneyStatus.CLOSED).orElse(null);
        boolean pregnant = journey != null
                && (journey.getStatus() == JourneyStatus.PREGNANCY || journey.getStatus() == JourneyStatus.PRE_DELIVERY);
        if (!pregnant || journey.getExpectedDeliveryDate() == null) {
            return new Duration(FULL_MONTHS, null);
        }
        long days = ChronoUnit.DAYS.between(LocalDate.now(), journey.getExpectedDeliveryDate());
        int months = (int) Math.ceil(days / AVERAGE_DAYS_PER_MONTH);
        return new Duration(Math.max(1, Math.min(FULL_MONTHS, months)), journey.getExpectedDeliveryDate());
    }

    /** full x months / 9, to whole rupees. 9 months returns the listed price exactly. */
    public static BigDecimal prorate(BigDecimal fullPrice, int months) {
        if (fullPrice == null) return null;
        if (months >= FULL_MONTHS) return fullPrice;
        return fullPrice.multiply(BigDecimal.valueOf(months))
                .divide(BigDecimal.valueOf(FULL_MONTHS), 0, RoundingMode.HALF_UP);
    }

    /** Sessions in proportion, rounded, but at least 1 wherever the full plan has any. */
    public static int prorateSessions(Integer fullSessions, int months) {
        if (fullSessions == null || fullSessions == 0) return 0;
        if (months >= FULL_MONTHS) return fullSessions;
        return Math.max(1, (int) Math.round(fullSessions * months / (double) FULL_MONTHS));
    }

    /** Installments never outnumber the months she's paying for. */
    public static int installmentCount(Integer planInstallments, int months) {
        int planned = planInstallments == null ? 1 : planInstallments;
        return Math.max(1, Math.min(planned, months));
    }
}
