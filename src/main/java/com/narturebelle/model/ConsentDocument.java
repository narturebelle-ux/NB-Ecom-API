package com.narturebelle.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** A versioned terms/privacy document. Only one row per type should be active=true at a time. */
@Entity
@Table(name = "consent_documents")
public class ConsentDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConsentDocumentType type;

    @Column(nullable = false)
    private Integer version;

    private String title;

    @Column(columnDefinition = "text")
    private String bodyMarkdown;

    private LocalDateTime effectiveAt;

    private boolean active;

    public ConsentDocument() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public ConsentDocumentType getType() { return type; }

    /** Derived from the type, not stored -- serialised as "required" so clients know which belong on the sign-up step. */
    public boolean isRequired() { return type != null && type.isRequired(); }
    public void setType(ConsentDocumentType type) { this.type = type; }

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
