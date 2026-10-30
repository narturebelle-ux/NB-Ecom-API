package com.narturebelle.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** A versioned onboarding document, same shape/convention as ConsentDocument. Only one row per
 * type should be active=true at a time. Seeded DRAFT, pending legal review -- see the roadmap's
 * Onboarding "Decisions needed" line. */
@Entity
@Table(name = "onboarding_documents")
public class OnboardingDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OnboardingDocumentType type;

    @Column(nullable = false)
    private Integer version;

    private String title;

    @Column(columnDefinition = "text")
    private String bodyMarkdown;

    private LocalDateTime effectiveAt;

    private boolean active;

    public OnboardingDocument() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public OnboardingDocumentType getType() { return type; }
    public void setType(OnboardingDocumentType type) { this.type = type; }

    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getBodyMarkdown() { return bodyMarkdown; }
    public void setBodyMarkdown(String bodyMarkdown) { this.bodyMarkdown = bodyMarkdown; }

    public LocalDateTime getEffectiveAt() { return effectiveAt; }
    public void setEffectiveAt(LocalDateTime effectiveAt) { this.effectiveAt = effectiveAt; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
