package com.narturebelle.model;

import jakarta.persistence.*;

/**
 * A seeded row in the stage -> professional map (blueprint §6). journeyStatus == null means the
 * step applies at every stage (e.g. the clinical psychologist offer) and is never window-gated.
 * professionalType reuses the Role enum (0.2) rather than a new type, since the values are identical.
 */
@Entity
@Table(name = "care_protocol_steps")
public class CareProtocolStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Integer version;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReviewStatus reviewStatus;

    @Enumerated(EnumType.STRING)
    private JourneyStatus journeyStatus;

    private Integer trimester;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role professionalType;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Column(nullable = false)
    private Integer sortOrder;

    /** "Offered, never pushed" items (e.g. clinical psychologist) are optional=true. */
    @Column(name = "is_optional", nullable = false)
    private boolean optional;

    public CareProtocolStep() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }

    public ReviewStatus getReviewStatus() { return reviewStatus; }
    public void setReviewStatus(ReviewStatus reviewStatus) { this.reviewStatus = reviewStatus; }

    public JourneyStatus getJourneyStatus() { return journeyStatus; }
    public void setJourneyStatus(JourneyStatus journeyStatus) { this.journeyStatus = journeyStatus; }

    public Integer getTrimester() { return trimester; }
    public void setTrimester(Integer trimester) { this.trimester = trimester; }

    public Role getProfessionalType() { return professionalType; }
    public void setProfessionalType(Role professionalType) { this.professionalType = professionalType; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    public boolean isOptional() { return optional; }
    public void setOptional(boolean optional) { this.optional = optional; }
}
