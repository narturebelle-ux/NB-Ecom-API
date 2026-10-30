package com.narturebelle.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** A care coordinator ("Belle person") who runs onboarding calls. Each call slot belongs to one, and
 * users only see slots from coordinators who speak one of their languages. */
@Entity
@Table(name = "coordinators")
public class Coordinator {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String displayName;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "coordinator_languages", joinColumns = @JoinColumn(name = "coordinator_id"))
    @Column(name = "language")
    private List<String> languages = new ArrayList<>();

    private String photoUrl;

    @Column(length = 1000)
    private String bio;

    private boolean active;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }

    public Coordinator() {}

    /** Case-insensitive: does she speak any of these? */
    public boolean speaksAnyOf(List<String> wanted) {
        return languages.stream().anyMatch(spoken -> wanted.stream().anyMatch(spoken::equalsIgnoreCase));
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public List<String> getLanguages() { return languages; }
    public void setLanguages(List<String> languages) { this.languages = languages; }

    public String getPhotoUrl() { return photoUrl; }
    public void setPhotoUrl(String photoUrl) { this.photoUrl = photoUrl; }

    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
