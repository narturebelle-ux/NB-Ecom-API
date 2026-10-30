package com.narturebelle.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** One member's vote on one idea. Whether it counts as a paid vote is decided at read time, so a
 * voter who buys a plan later starts counting automatically. */
@Entity
@Table(name = "idea_votes", uniqueConstraints = @UniqueConstraint(columnNames = {"idea_id", "user_id"}))
public class IdeaVote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long ideaId;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }

    public IdeaVote() {}

    public IdeaVote(Long ideaId, Long userId) {
        this.ideaId = ideaId;
        this.userId = userId;
    }

    public Long getId() { return id; }
    public Long getIdeaId() { return ideaId; }
    public Long getUserId() { return userId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
