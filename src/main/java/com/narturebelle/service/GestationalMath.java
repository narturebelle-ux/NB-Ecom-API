package com.narturebelle.service;

import com.narturebelle.model.JourneyStatus;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Shared by JourneyResponse and DashboardService so the formula lives in exactly one place. */
public final class GestationalMath {

    private GestationalMath() {}

    /** Per the brief: EDD - 280 days = day 0 (week 1 day 1). Null outside an active PREGNANCY journey or without an EDD. */
    public static Integer gestationalWeek(JourneyStatus status, LocalDate expectedDeliveryDate) {
        if (status != JourneyStatus.PREGNANCY || expectedDeliveryDate == null) {
            return null;
        }
        LocalDate day0 = expectedDeliveryDate.minusDays(280);
        long daysSinceDay0 = ChronoUnit.DAYS.between(day0, LocalDate.now());
        return (int) Math.floorDiv(daysSinceDay0, 7) + 1;
    }

    /** Trimester 1: weeks 1-13, 2: 14-27, 3: 28+. Null when the week itself is null or not yet started. */
    public static Integer trimester(JourneyStatus status, LocalDate expectedDeliveryDate) {
        Integer week = gestationalWeek(status, expectedDeliveryDate);
        if (week == null || week < 1) {
            return null;
        }
        return week <= 13 ? 1 : week <= 27 ? 2 : 3;
    }
}
