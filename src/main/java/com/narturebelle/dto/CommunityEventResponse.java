package com.narturebelle.dto;

import com.narturebelle.model.CommunityEvent;
import com.narturebelle.model.CommunityEventStatus;
import com.narturebelle.model.Modality;

import java.time.LocalDateTime;

/** Public view of a Joy Circle event -- never includes joinUrl (see CommunityEventRsvpResponse). */
public class CommunityEventResponse {

    /** Placeholder wording pending review, same status as the other seeded copy. */
    public static final String DISCLAIMER =
            "Joy Circle events are non-clinical community gatherings. They are not medical advice -- "
                    + "for health concerns, speak to your doctor.";

    private Long id;
    private String title;
    private String description;
    private String hostName;
    private String hostBio;
    private String category;
    private LocalDateTime startsAt;
    private LocalDateTime endsAt;
    private Modality modality;
    private String location;
    private Integer capacity;
    private long rsvpCount;
    private Long spotsLeft;
    private CommunityEventStatus status;
    private String disclaimer;

    public static CommunityEventResponse from(CommunityEvent event, long rsvpCount) {
        CommunityEventResponse dto = new CommunityEventResponse();
        dto.id = event.getId();
        dto.title = event.getTitle();
        dto.description = event.getDescription();
        dto.hostName = event.getHostName();
        dto.hostBio = event.getHostBio();
        dto.category = event.getCategory();
        dto.startsAt = event.getStartsAt();
        dto.endsAt = event.getEndsAt();
        dto.modality = event.getModality();
        dto.location = event.getLocation();
        dto.capacity = event.getCapacity();
        dto.rsvpCount = rsvpCount;
        dto.spotsLeft = event.getCapacity() == null ? null : Math.max(0, event.getCapacity() - rsvpCount);
        dto.status = event.getStatus();
        dto.disclaimer = DISCLAIMER;
        return dto;
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getHostName() { return hostName; }
    public String getHostBio() { return hostBio; }
    public String getCategory() { return category; }
    public LocalDateTime getStartsAt() { return startsAt; }
    public LocalDateTime getEndsAt() { return endsAt; }
    public Modality getModality() { return modality; }
    public String getLocation() { return location; }
    public Integer getCapacity() { return capacity; }
    public long getRsvpCount() { return rsvpCount; }
    public Long getSpotsLeft() { return spotsLeft; }
    public CommunityEventStatus getStatus() { return status; }
    public String getDisclaimer() { return disclaimer; }
}
