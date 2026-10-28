package com.narturebelle.dto;

import com.narturebelle.model.Journey;
import com.narturebelle.model.JourneyStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * gestationalWeek/trimester are computed, never persisted — only meaningful for an active
 * PREGNANCY journey with an EDD on file. Per the brief: day 0 = EDD - 280 days; trimester 1
 * is weeks 1-13, 2 is 14-27, 3 is 28+.
 */
public class JourneyResponse {

    private Long id;
    private JourneyStatus status;
    private LocalDate expectedDeliveryDate;
    private LocalDateTime startedAt;
    private LocalDateTime closedAt;
    private Integer gestationalWeek;
    private Integer trimester;

    public static JourneyResponse from(Journey journey) {
        JourneyResponse dto = new JourneyResponse();
        dto.id = journey.getId();
        dto.status = journey.getStatus();
        dto.expectedDeliveryDate = journey.getExpectedDeliveryDate();
        dto.startedAt = journey.getStartedAt();
        dto.closedAt = journey.getClosedAt();

        if (journey.getStatus() == JourneyStatus.PREGNANCY && journey.getExpectedDeliveryDate() != null) {
            LocalDate day0 = journey.getExpectedDeliveryDate().minusDays(280);
            long daysSinceDay0 = ChronoUnit.DAYS.between(day0, LocalDate.now());
            int week = (int) Math.floorDiv(daysSinceDay0, 7) + 1;
            dto.gestationalWeek = week;
            if (week >= 1) {
                dto.trimester = week <= 13 ? 1 : week <= 27 ? 2 : 3;
            }
        }

        return dto;
    }

    public Long getId() { return id; }
    public JourneyStatus getStatus() { return status; }
    public LocalDate getExpectedDeliveryDate() { return expectedDeliveryDate; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public LocalDateTime getClosedAt() { return closedAt; }
    public Integer getGestationalWeek() { return gestationalWeek; }
    public Integer getTrimester() { return trimester; }
}
