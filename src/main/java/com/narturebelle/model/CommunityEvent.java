package com.narturebelle.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** A Joy Circle event: a non-clinical community gathering led by a host, not a doctor. Deliberately a
 * separate model from DoctorSession, which presents medical credentials (see the roadmap's Phase 6 note).
 * joinUrl is only ever returned to users who have RSVP'd. */
@Entity
@Table(name = "community_events")
public class CommunityEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(length = 2000)
    private String description;

    @Column(nullable = false)
    private String hostName;

    @Column(length = 1000)
    private String hostBio;

    private String category;

    @Column(nullable = false)
    private LocalDateTime startsAt;

    @Column(nullable = false)
    private LocalDateTime endsAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Modality modality;

    private String location;

    private String joinUrl;

    /** Null means unlimited. */
    private Integer capacity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CommunityEventStatus status;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }

    public CommunityEvent() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getHostName() { return hostName; }
    public void setHostName(String hostName) { this.hostName = hostName; }

    public String getHostBio() { return hostBio; }
    public void setHostBio(String hostBio) { this.hostBio = hostBio; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public LocalDateTime getStartsAt() { return startsAt; }
    public void setStartsAt(LocalDateTime startsAt) { this.startsAt = startsAt; }

    public LocalDateTime getEndsAt() { return endsAt; }
    public void setEndsAt(LocalDateTime endsAt) { this.endsAt = endsAt; }

    public Modality getModality() { return modality; }
    public void setModality(Modality modality) { this.modality = modality; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getJoinUrl() { return joinUrl; }
    public void setJoinUrl(String joinUrl) { this.joinUrl = joinUrl; }

    public Integer getCapacity() { return capacity; }
    public void setCapacity(Integer capacity) { this.capacity = capacity; }

    public CommunityEventStatus getStatus() { return status; }
    public void setStatus(CommunityEventStatus status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
