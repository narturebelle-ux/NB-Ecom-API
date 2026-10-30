package com.narturebelle.service;

import com.narturebelle.dto.CommunityEventRsvpResponse;
import com.narturebelle.dto.CreateCommunityEventRequest;
import com.narturebelle.exception.CommunityEventFullException;
import com.narturebelle.exception.CommunityEventNotFoundException;
import com.narturebelle.exception.CommunityEventNotOpenException;
import com.narturebelle.exception.InvalidCommunityEventRequestException;
import com.narturebelle.model.CommunityEvent;
import com.narturebelle.model.CommunityEventRsvp;
import com.narturebelle.model.CommunityEventStatus;
import com.narturebelle.repository.CommunityEventRepository;
import com.narturebelle.repository.CommunityEventRsvpRepository;
import com.narturebelle.security.AuditService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Joy Circle (Phase 6): non-clinical community events + RSVPs. */
@Service
public class CommunityEventService {

    private final CommunityEventRepository communityEventRepository;
    private final CommunityEventRsvpRepository communityEventRsvpRepository;
    private final AuditService auditService;

    public CommunityEventService(CommunityEventRepository communityEventRepository,
                                 CommunityEventRsvpRepository communityEventRsvpRepository,
                                 AuditService auditService) {
        this.communityEventRepository = communityEventRepository;
        this.communityEventRsvpRepository = communityEventRsvpRepository;
        this.auditService = auditService;
    }

    /** Upcoming = not yet ended, so an event in progress still shows. */
    public List<CommunityEvent> listEvents(boolean includePast) {
        return includePast
                ? communityEventRepository.findAllByOrderByStartsAtAsc()
                : communityEventRepository.findByEndsAtAfterOrderByStartsAtAsc(LocalDateTime.now());
    }

    public CommunityEvent getEvent(Long eventId) {
        return communityEventRepository.findById(eventId)
                .orElseThrow(() -> new CommunityEventNotFoundException("Event " + eventId + " not found."));
    }

    public long rsvpCount(Long eventId) {
        return communityEventRsvpRepository.countByEventId(eventId);
    }

    /**
     * Idempotent: RSVPing twice returns the existing RSVP. The event row is locked for the
     * duration so two concurrent RSVPs can't both take the last spot; the (event, user) unique
     * constraint backstops the duplicate check. The returned event's rsvpCount includes this RSVP.
     */
    @Transactional
    public CommunityEventRsvpResponse rsvp(Long userId, Long eventId) {
        CommunityEvent event = communityEventRepository.findByIdForUpdate(eventId)
                .orElseThrow(() -> new CommunityEventNotFoundException("Event " + eventId + " not found."));

        Optional<CommunityEventRsvp> existing = communityEventRsvpRepository.findByEventIdAndUserId(eventId, userId);
        if (existing.isPresent()) {
            return CommunityEventRsvpResponse.from(existing.get(), event, rsvpCount(eventId));
        }

        if (event.getStatus() != CommunityEventStatus.SCHEDULED || !event.getStartsAt().isAfter(LocalDateTime.now())) {
            throw new CommunityEventNotOpenException("This event is no longer open for RSVPs.");
        }
        long count = rsvpCount(eventId);
        if (event.getCapacity() != null && count >= event.getCapacity()) {
            throw new CommunityEventFullException("This event is full.");
        }

        CommunityEventRsvp rsvp = new CommunityEventRsvp();
        rsvp.setEventId(eventId);
        rsvp.setUserId(userId);
        CommunityEventRsvp saved = communityEventRsvpRepository.save(rsvp);
        auditService.record("COMMUNITY_EVENT_RSVP", "COMMUNITY_EVENT", String.valueOf(eventId), "User RSVP'd to a Joy Circle event");
        return CommunityEventRsvpResponse.from(saved, event, count + 1);
    }

    /** Idempotent: cancelling an RSVP that doesn't exist is a no-op. */
    @Transactional
    public void cancelRsvp(Long userId, Long eventId) {
        getEvent(eventId);
        communityEventRsvpRepository.findByEventIdAndUserId(eventId, userId).ifPresent(rsvp -> {
            communityEventRsvpRepository.delete(rsvp);
            auditService.record("COMMUNITY_EVENT_RSVP_CANCELLED", "COMMUNITY_EVENT", String.valueOf(eventId),
                    "User cancelled a Joy Circle RSVP");
        });
    }

    /** The caller's RSVPs, soonest event first. */
    public List<CommunityEventRsvpResponse> listRsvps(Long userId) {
        return communityEventRsvpRepository.findByUserId(userId).stream()
                .map(rsvp -> {
                    CommunityEvent event = getEvent(rsvp.getEventId());
                    return CommunityEventRsvpResponse.from(rsvp, event, rsvpCount(event.getId()));
                })
                .sorted(Comparator.comparing(r -> r.getEvent().getStartsAt()))
                .toList();
    }

    public CommunityEvent createEvent(CreateCommunityEventRequest req) {
        if (!req.getEndsAt().isAfter(req.getStartsAt())) {
            throw new InvalidCommunityEventRequestException("endsAt", "must be after startsAt");
        }
        CommunityEvent event = new CommunityEvent();
        event.setTitle(req.getTitle());
        event.setDescription(req.getDescription());
        event.setHostName(req.getHostName());
        event.setHostBio(req.getHostBio());
        event.setCategory(req.getCategory());
        event.setStartsAt(req.getStartsAt());
        event.setEndsAt(req.getEndsAt());
        event.setModality(req.getModality());
        event.setLocation(req.getLocation());
        event.setJoinUrl(req.getJoinUrl());
        event.setCapacity(req.getCapacity());
        event.setStatus(CommunityEventStatus.SCHEDULED);
        CommunityEvent saved = communityEventRepository.save(event);
        auditService.record("COMMUNITY_EVENT_CREATED", "COMMUNITY_EVENT", String.valueOf(saved.getId()), "Admin created a Joy Circle event");
        return saved;
    }

    @Transactional
    public CommunityEvent cancelEvent(Long eventId) {
        CommunityEvent event = getEvent(eventId);
        if (event.getStatus() == CommunityEventStatus.CANCELLED) {
            return event;
        }
        event.setStatus(CommunityEventStatus.CANCELLED);
        CommunityEvent saved = communityEventRepository.save(event);
        auditService.record("COMMUNITY_EVENT_CANCELLED", "COMMUNITY_EVENT", String.valueOf(saved.getId()), "Admin cancelled a Joy Circle event");
        return saved;
    }
}
