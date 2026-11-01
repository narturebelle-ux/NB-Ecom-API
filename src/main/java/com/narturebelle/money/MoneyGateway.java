package com.narturebelle.money;

import com.narturebelle.model.PlanCode;
import com.narturebelle.model.Role;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * What Care needs to know about money. Answered from this API's own tables until cut-over to
 * NB-Ecom-Payments-API (set money.base-url), and only ever from there after that.
 */
public interface MoneyGateway {

    record SessionGrant(Role professionalType, int sessionCount) {}

    record RewardOrder(Long orderId, PlanCode planCode, LocalDateTime expiresAt, List<SessionGrant> sessions) {}

    boolean hasAnyOrder(Long userId);

    boolean hasPaidOrder(Long userId);

    /** A real purchase: PAID with a price above zero (a referral-reward order is free and doesn't count). */
    boolean hasPaidOrderAboveZero(Long userId);

    /** Her orders and payments for the account export, as named lists. */
    Map<String, Object> moneyExport(Long userId);

    /** A free PAID order for this plan. Safe to repeat: the same rewardKey returns the same order. */
    RewardOrder grantReward(String rewardKey, Long userId, PlanCode planCode);
}
