package com.narturebelle.service;

import com.narturebelle.dto.CreateJourneyRequest;
import com.narturebelle.dto.PatchJourneyRequest;
import com.narturebelle.exception.DueDateLockedException;
import com.narturebelle.exception.InvalidJourneyRequestException;
import com.narturebelle.exception.JourneyAlreadyActiveException;
import com.narturebelle.model.Journey;
import com.narturebelle.model.JourneyEventSource;
import com.narturebelle.model.JourneyStageEvent;
import com.narturebelle.model.JourneyStatus;
import com.narturebelle.model.Order;
import com.narturebelle.model.OrderStatus;
import com.narturebelle.repository.JourneyRepository;
import com.narturebelle.repository.OrderRepository;
import com.narturebelle.repository.JourneyStageEventRepository;
import com.narturebelle.security.AuditService;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class JourneyService {

    private final JourneyRepository journeyRepository;
    private final JourneyStageEventRepository journeyStageEventRepository;
    private final AuditService auditService;
    private final OrderRepository orderRepository;

    public JourneyService(JourneyRepository journeyRepository, JourneyStageEventRepository journeyStageEventRepository,
                           AuditService auditService, OrderRepository orderRepository) {
        this.journeyRepository = journeyRepository;
        this.journeyStageEventRepository = journeyStageEventRepository;
        this.auditService = auditService;
        this.orderRepository = orderRepository;
    }

    public Journey create(Long userId, CreateJourneyRequest req) {
        if (journeyRepository.existsByUserIdAndStatusNot(userId, JourneyStatus.CLOSED)) {
            throw new JourneyAlreadyActiveException("An active journey already exists. Close it before starting a new one.");
        }
        validateExpectedDeliveryDate(req.getExpectedDeliveryDate());
        guardPricedDueDate(userId, req.getExpectedDeliveryDate());

        Journey journey = new Journey();
        journey.setUserId(userId);
        journey.setStatus(req.getStatus());
        journey.setExpectedDeliveryDate(req.getExpectedDeliveryDate());
        Journey saved = journeyRepository.save(journey);

        recordStageEvent(saved.getId(), null, saved.getStatus(), userId, JourneyEventSource.USER);
        auditService.record("JOURNEY_CREATED", "Journey", String.valueOf(saved.getId()), null,
                Map.of("status", saved.getStatus().name()));

        return saved;
    }

    public List<Journey> listForUser(Long userId) {
        return journeyRepository.findByUserIdOrderByStartedAtDesc(userId);
    }

    public Optional<Journey> find(Long id) {
        return journeyRepository.findById(id);
    }

    /** Caller is responsible for the ownership check before calling this. */
    public Journey update(Journey journey, PatchJourneyRequest req) {
        JourneyStatus previousStatus = journey.getStatus();

        if (req.getExpectedDeliveryDate() != null) {
            validateExpectedDeliveryDate(req.getExpectedDeliveryDate());
            guardPricedDueDate(journey.getUserId(), req.getExpectedDeliveryDate());
            journey.setExpectedDeliveryDate(req.getExpectedDeliveryDate());
        }
        boolean statusChanged = req.getStatus() != null && req.getStatus() != previousStatus;
        if (req.getStatus() != null) {
            journey.setStatus(req.getStatus());
            if (req.getStatus() == JourneyStatus.CLOSED) {
                journey.setClosedAt(LocalDateTime.now());
            }
        }

        Journey saved = journeyRepository.save(journey);

        if (statusChanged) {
            recordStageEvent(saved.getId(), previousStatus, saved.getStatus(), saved.getUserId(), JourneyEventSource.USER);
        }
        auditService.record("JOURNEY_UPDATED", "Journey", String.valueOf(saved.getId()), null,
                Map.of("previousStatus", previousStatus.name(), "status", saved.getStatus().name()));

        return saved;
    }

    private void recordStageEvent(Long journeyId, JourneyStatus from, JourneyStatus to, Long actorUserId,
                                   JourneyEventSource source) {
        JourneyStageEvent event = new JourneyStageEvent();
        event.setJourneyId(journeyId);
        event.setFromStatus(from);
        event.setToStatus(to);
        event.setActorUserId(actorUserId);
        event.setSource(source);
        journeyStageEventRepository.save(event);
    }

    /**
     * Anti-gaming for duration-based pricing: plans are priced on the months left until her EDD, so once she
     * has a pending or paid order priced on one, the EDD may only move within this many days of it -- room
     * for a dating-scan correction, not for "enter a near due date, pay less, move it back". Applies to editing
     * the journey and to closing it and starting a new one. Bigger corrections go through support.
     */
    static final int PRICED_DUE_DATE_TOLERANCE_DAYS = 21;

    private void guardPricedDueDate(Long userId, LocalDate newEdd) {
        if (newEdd == null) return;
        for (Order order : orderRepository.findByUserIdAndStatusInAndPricedDueDateIsNotNull(
                userId, List.of(OrderStatus.PENDING, OrderStatus.PAID))) {
            long drift = Math.abs(ChronoUnit.DAYS.between(order.getPricedDueDate(), newEdd));
            if (drift > PRICED_DUE_DATE_TOLERANCE_DAYS) {
                auditService.record("JOURNEY_EDD_CHANGE_BLOCKED", "Order", String.valueOf(order.getId()),
                        "Due date change too far from the date a plan was priced on");
                throw new DueDateLockedException("Your plan was priced on a due date of " + order.getPricedDueDate()
                        + ". It can only be changed by up to " + PRICED_DUE_DATE_TOLERANCE_DAYS
                        + " days -- please contact support for a bigger correction.");
            }
        }
    }

    private void validateExpectedDeliveryDate(LocalDate edd) {
        if (edd == null) return;
        LocalDate today = LocalDate.now();
        if (edd.isAfter(today.plusWeeks(42))) {
            throw new InvalidJourneyRequestException("expectedDeliveryDate",
                    "Expected delivery date may not be more than 42 weeks in the future.");
        }
        if (edd.isBefore(today.minusYears(1))) {
            throw new InvalidJourneyRequestException("expectedDeliveryDate",
                    "Expected delivery date may not be more than 1 year in the past.");
        }
    }
}
