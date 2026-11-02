package com.narturebelle.mail;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** A send request waiting for NB-Ecom-Notifications-API. See V31__outgoing_emails.sql. */
@Entity
@Table(name = "outgoing_emails")
public class OutgoingEmail {

    public enum Kind { EMAIL, TRANSACTIONAL }

    public enum Status { PENDING, SENT, FAILED }

    /** What to record on the source row once the email really went out. */
    public enum OnDelivered { JOB_APPLICATION, FEEDBACK }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 120)
    private String eventId;

    @Column(nullable = false, length = 80)
    private String eventType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Kind kind;

    private Long userId;

    @Column(length = 320)
    private String toAddress;

    @Column(length = 320)
    private String replyTo;

    @Column(columnDefinition = "text")
    private String subject;

    @Column(columnDefinition = "text")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private OnDelivered onDelivered;

    private Long sourceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PENDING;

    @Column(nullable = false)
    private int attempts;

    private LocalDateTime lastAttemptAt;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime sentAt;

    public OutgoingEmail() {}

    public Long getId() { return id; }
    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public Kind getKind() { return kind; }
    public void setKind(Kind kind) { this.kind = kind; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getToAddress() { return toAddress; }
    public void setToAddress(String toAddress) { this.toAddress = toAddress; }
    public String getReplyTo() { return replyTo; }
    public void setReplyTo(String replyTo) { this.replyTo = replyTo; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public OnDelivered getOnDelivered() { return onDelivered; }
    public void setOnDelivered(OnDelivered onDelivered) { this.onDelivered = onDelivered; }
    public Long getSourceId() { return sourceId; }
    public void setSourceId(Long sourceId) { this.sourceId = sourceId; }
    public Status getStatus() { return status; }
    public int getAttempts() { return attempts; }
    public LocalDateTime getSentAt() { return sentAt; }

    public void recordFailedAttempt(int maxAttempts) {
        this.attempts++;
        this.lastAttemptAt = LocalDateTime.now();
        if (this.attempts >= maxAttempts) {
            this.status = Status.FAILED;
        }
    }

    /** Personal data stays only until the email is out. */
    public void markSent() {
        this.attempts++;
        this.lastAttemptAt = LocalDateTime.now();
        this.status = Status.SENT;
        this.sentAt = this.lastAttemptAt;
        this.toAddress = null;
        this.replyTo = null;
        this.subject = null;
        this.body = null;
    }
}
