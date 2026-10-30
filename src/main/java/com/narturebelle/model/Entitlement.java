package com.narturebelle.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** A real session-wallet row, granted only after confirmed payment. usedSessions stays 0 until Phase 3's booking/session-completion flow exists. */
@Entity
@Table(name = "entitlements", uniqueConstraints = @UniqueConstraint(columnNames = {"order_id", "professional_type"}))
public class Entitlement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "professional_type", nullable = false)
    private Role professionalType;

    @Column(nullable = false)
    private Integer totalSessions;

    @Column(nullable = false)
    private Integer usedSessions = 0;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }

    public Entitlement() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }

    public Role getProfessionalType() { return professionalType; }
    public void setProfessionalType(Role professionalType) { this.professionalType = professionalType; }

    public Integer getTotalSessions() { return totalSessions; }
    public void setTotalSessions(Integer totalSessions) { this.totalSessions = totalSessions; }

    public Integer getUsedSessions() { return usedSessions; }
    public void setUsedSessions(Integer usedSessions) { this.usedSessions = usedSessions; }

    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
