package com.narturebelle.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** Append-only history of a journey's status transitions. */
@Entity
@Table(name = "journey_stage_events")
public class JourneyStageEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long journeyId;

    @Enumerated(EnumType.STRING)
    private JourneyStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JourneyStatus toStatus;

    @Column(nullable = false)
    private LocalDateTime occurredAt;

    private Long actorUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JourneyEventSource source;

    @PrePersist
    protected void onCreate() {
        if (this.occurredAt == null) {
            this.occurredAt = LocalDateTime.now();
        }
    }

    public JourneyStageEvent() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getJourneyId() { return journeyId; }
    public void setJourneyId(Long journeyId) { this.journeyId = journeyId; }

    public JourneyStatus getFromStatus() { return fromStatus; }
    public void setFromStatus(JourneyStatus fromStatus) { this.fromStatus = fromStatus; }

    public JourneyStatus getToStatus() { return toStatus; }
    public void setToStatus(JourneyStatus toStatus) { this.toStatus = toStatus; }

    public LocalDateTime getOccurredAt() { return occurredAt; }
    public void setOccurredAt(LocalDateTime occurredAt) { this.occurredAt = occurredAt; }

    public Long getActorUserId() { return actorUserId; }
    public void setActorUserId(Long actorUserId) { this.actorUserId = actorUserId; }

    public JourneyEventSource getSource() { return source; }
    public void setSource(JourneyEventSource source) { this.source = source; }
}
