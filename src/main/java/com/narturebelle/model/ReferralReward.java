package com.narturebelle.model;

import jakarta.persistence.*;
import org.springframework.data.domain.Persistable;

import java.time.LocalDateTime;

/** The free Premium plan a member earned through referrals -- once per member, granted as a zero-price PAID order.
 * Always inserted (Persistable.isNew), so a second grant hits the primary key instead of silently merging. */
@Entity
@Table(name = "referral_rewards")
public class ReferralReward implements Persistable<Long> {

    @Id
    private Long userId;

    @Column(nullable = false)
    private Long orderId;

    @Column(nullable = false)
    private LocalDateTime grantedAt;

    public ReferralReward() {}

    public ReferralReward(Long userId, Long orderId) {
        this.userId = userId;
        this.orderId = orderId;
        this.grantedAt = LocalDateTime.now();
    }

    @Override
    public Long getId() { return userId; }

    @Override
    public boolean isNew() { return true; }

    public Long getUserId() { return userId; }
    public Long getOrderId() { return orderId; }
    public LocalDateTime getGrantedAt() { return grantedAt; }
}
