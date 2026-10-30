package com.narturebelle.service;

import com.narturebelle.dto.CreateJourneyRequest;
import com.narturebelle.dto.InputRules;
import com.narturebelle.dto.PatchJourneyRequest;
import com.narturebelle.exception.InvalidJourneyRequestException;
import com.narturebelle.exception.JourneyAlreadyActiveException;
import com.narturebelle.model.Journey;
import com.narturebelle.model.JourneyEventSource;
import com.narturebelle.model.JourneyStageEvent;
import com.narturebelle.model.JourneyStatus;
import com.narturebelle.repository.JourneyRepository;
import com.narturebelle.repository.JourneyStageEventRepository;
import com.narturebelle.security.AuditService;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class JourneyService {

    private final JourneyRepository journeyRepository;
    private final JourneyStageEventRepository journeyStageEventRepository;
    private final AuditService auditService;

    public JourneyService(JourneyRepository journeyRepository, JourneyStageEventRepository journeyStageEventRepository,
                           AuditService auditService) {
        this.journeyRepository = journeyRepository;
        this.journeyStageEventRepository = journeyStageEventRepository;
        this.auditService = auditService;
    }

    public Journey create(Long userId, CreateJourneyRequest req) {
        if (journeyRepository.existsByUserIdAndStatusNot(userId, JourneyStatus.CLOSED)) {
            throw new JourneyAlreadyActiveException("An active journey already exists. Close it before starting a new one.");
        }
        validateExpectedDeliveryDate(req.getStatus(), req.getExpectedDeliveryDate());
        requireDueDateIfPregnant(req.getStatus(), req.getExpectedDeliveryDate());

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

        JourneyStatus newStatus = req.getStatus() != null ? req.getStatus() : previousStatus;
        if (req.getExpectedDeliveryDate() != null) {
            validateExpectedDeliveryDate(newStatus, req.getExpectedDeliveryDate());
            journey.setExpectedDeliveryDate(req.getExpectedDeliveryDate());
        }
        requireDueDateIfPregnant(newStatus, journey.getExpectedDeliveryDate());
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

    private static boolean pregnant(JourneyStatus status) {
        return status == JourneyStatus.PREGNANCY || status == JourneyStatus.PRE_DELIVERY;
    }

    private void requireDueDateIfPregnant(JourneyStatus status, LocalDate edd) {
        if (pregnant(status) && edd == null) {
            throw new InvalidJourneyRequestException("expectedDeliveryDate", "Please enter your due date.");
        }
    }

    /** Still pregnant: today up to 42 weeks ahead. After delivery, it's the date she delivered: not in the future. */
    private void validateExpectedDeliveryDate(JourneyStatus status, LocalDate edd) {
        if (edd == null) return;
        LocalDate today = LocalDate.now();
        if (pregnant(status)) {
            if (edd.isBefore(today)) {
                throw new InvalidJourneyRequestException("expectedDeliveryDate", "Due date can't be in the past.");
            }
            if (edd.isAfter(today.plusWeeks(InputRules.MAX_WEEKS_TO_DUE_DATE))) {
                throw new InvalidJourneyRequestException("expectedDeliveryDate",
                        "Due date can't be more than " + InputRules.MAX_WEEKS_TO_DUE_DATE + " weeks away.");
            }
        } else if (edd.isAfter(today)) {
            throw new InvalidJourneyRequestException("expectedDeliveryDate", "Delivery date can't be in the future.");
        }
    }
}
