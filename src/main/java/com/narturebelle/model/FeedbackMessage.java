package com.narturebelle.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** An "ideas / feedback" submission from the website, signed in or not. */
@Entity
@Table(name = "feedback_messages")
public class FeedbackMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Set when the sender was signed in. */
    private Long userId;

    @Column(length = 120)
    private String name;

    @Column(length = 254)
    private String email;

    @Column(nullable = false, length = 3000)
    private String message;

    private Integer rating;

    @Column(length = 300)
    private String page;

    @Column(nullable = false)
    private LocalDateTime submittedAt;

    private LocalDateTime adminNotifiedAt;

    @PrePersist
    protected void onCreate() {
        if (this.submittedAt == null) {
            this.submittedAt = LocalDateTime.now();
        }
    }

    public FeedbackMessage() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public Integer getRating() { return rating; }
    public void setRating(Integer rating) { this.rating = rating; }

    public String getPage() { return page; }
    public void setPage(String page) { this.page = page; }

    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }

    public LocalDateTime getAdminNotifiedAt() { return adminNotifiedAt; }
    public void setAdminNotifiedAt(LocalDateTime adminNotifiedAt) { this.adminNotifiedAt = adminNotifiedAt; }
}
