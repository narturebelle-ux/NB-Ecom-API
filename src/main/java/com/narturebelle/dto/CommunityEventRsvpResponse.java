package com.narturebelle.dto;

import com.narturebelle.model.CommunityEvent;
import com.narturebelle.model.CommunityEventRsvp;

import java.time.LocalDateTime;

/** The only response that carries an event's joinUrl -- the caller has RSVP'd. */
public class CommunityEventRsvpResponse {

    private Long eventId;
    private CommunityEventResponse event;
    private String joinUrl;
    private LocalDateTime rsvpAt;

    public static CommunityEventRsvpResponse from(CommunityEventRsvp rsvp, CommunityEvent event, long rsvpCount) {
        CommunityEventRsvpResponse dto = new CommunityEventRsvpResponse();
        dto.eventId = event.getId();
        dto.event = CommunityEventResponse.from(event, rsvpCount);
        dto.joinUrl = event.getJoinUrl();
        dto.rsvpAt = rsvp.getCreatedAt();
        return dto;
    }

    public Long getEventId() { return eventId; }
    public CommunityEventResponse getEvent() { return event; }
    public String getJoinUrl() { return joinUrl; }
    public LocalDateTime getRsvpAt() { return rsvpAt; }
}
