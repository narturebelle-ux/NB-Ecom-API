package com.narturebelle.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Config, not code — mirrors PlanEntitlement. One flat placeholder rate per professional type, no effective-dated versioning. */
@Entity
@Table(name = "payout_rates")
public class PayoutRate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true)
    private Role professionalType;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void touch() {
        this.updatedAt = LocalDateTime.now();
    }

    public PayoutRate() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Role getProfessionalType() { return professionalType; }
    public void setProfessionalType(Role professionalType) { this.professionalType = professionalType; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
