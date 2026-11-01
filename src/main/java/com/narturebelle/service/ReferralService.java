package com.narturebelle.service;

import com.narturebelle.mail.MailQueue;
import com.narturebelle.exception.ReferralException;
import com.narturebelle.model.*;
import com.narturebelle.money.MoneyGateway;
import com.narturebelle.repository.*;
import com.narturebelle.security.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Referral program (product-owner decision, 2026-10-30): every member has a personal link; a friend who
 * joins through it and buys a plan qualifies; after 5 qualified friends the member earns a free Premium
 * plan -- full 9 months, every professional it includes -- once. Only the member who shared is rewarded.
 */
@Service
public class ReferralService {

    private static final Logger log = LoggerFactory.getLogger(ReferralService.class);
    public static final int TARGET = 5;
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // no 0/O, 1/I
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ReferralCodeRepository referralCodeRepository;
    private final ReferralRepository referralRepository;
    private final ReferralRewardRepository referralRewardRepository;
    private final MoneyGateway moneyGateway;
    private final EntitlementService entitlementService;
    private final UserRepository userRepository;
    private final MailQueue mailQueue;
    private final AuditService auditService;
    private final TransactionTemplate requiresNew;

    public ReferralService(ReferralCodeRepository referralCodeRepository, ReferralRepository referralRepository,
                           ReferralRewardRepository referralRewardRepository, MoneyGateway moneyGateway,
                           EntitlementService entitlementService, UserRepository userRepository,
                           MailQueue mailQueue, AuditService auditService,
                           PlatformTransactionManager transactionManager) {
        this.referralCodeRepository = referralCodeRepository;
        this.referralRepository = referralRepository;
        this.referralRewardRepository = referralRewardRepository;
        this.moneyGateway = moneyGateway;
        this.entitlementService = entitlementService;
        this.userRepository = userRepository;
        this.mailQueue = mailQueue;
        this.auditService = auditService;
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public record Friend(String name, LocalDateTime joinedAt, boolean purchased) {}

    public record Summary(String code, int target, long invited, long qualified, boolean rewardGranted,
                          Long rewardOrderId, List<Friend> friends) {}

    /** Her code (created on first call), progress toward the reward, and the friends who joined. */
    public Summary summary(Long userId) {
        String code = codeFor(userId);
        List<Referral> referrals = referralRepository.findByReferrerUserIdOrderByCreatedAtDesc(userId);
        Map<Long, User> friends = userRepository.findAllById(referrals.stream().map(Referral::getReferredUserId).toList())
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        List<Friend> list = referrals.stream()
                .map(r -> new Friend(displayName(friends.get(r.getReferredUserId())), r.getCreatedAt(), r.getQualifiedAt() != null))
                .toList();
        long qualified = list.stream().filter(Friend::purchased).count();
        ReferralReward reward = referralRewardRepository.findById(userId).orElse(null);
        return new Summary(code, TARGET, list.size(), qualified, reward != null,
                reward == null ? null : reward.getOrderId(), list);
    }

    /** Public: who shared this code, for the "Asha R. invited you" banner on the landing page. */
    public String referrerName(String code) {
        ReferralCode found = referralCodeRepository.findByCode(normalise(code))
                .orElseThrow(ReferralService::codeNotFound);
        return displayName(userRepository.findById(found.getUserId()).orElse(null));
    }

    /** A new member links herself to whoever shared the code. Only before her first purchase, and only once. */
    public String claim(Long userId, String code) {
        ReferralCode found = referralCodeRepository.findByCode(normalise(code))
                .orElseThrow(ReferralService::codeNotFound);
        if (found.getUserId().equals(userId)) {
            throw new ReferralException(409, "REFERRAL_SELF", "That's your own invite link -- share it with friends!");
        }
        if (referralRepository.findByReferredUserId(userId).isPresent()) {
            throw new ReferralException(409, "REFERRAL_ALREADY_CLAIMED", "You've already joined through an invite.");
        }
        if (purchased(userId)) {
            throw new ReferralException(409, "REFERRAL_NOT_NEW_CUSTOMER", "Invites are for new members who haven't bought a plan yet.");
        }
        try {
            referralRepository.save(new Referral(found.getUserId(), userId));
        } catch (DataIntegrityViolationException raced) {
            throw new ReferralException(409, "REFERRAL_ALREADY_CLAIMED", "You've already joined through an invite.");
        }
        auditService.record("REFERRAL_CLAIMED", "USER", String.valueOf(userId), "Joined through a referral link");
        return displayName(userRepository.findById(found.getUserId()).orElse(null));
    }

    /**
     * Called when an order becomes PAID. If its buyer was referred, the referral qualifies; at the target,
     * the referrer gets her free Premium plan. Idempotent: replays and the reward order itself (price 0) are no-ops.
     */
    public void onOrderPaid(Order order) {
        try {
            onPaid(order.getId(), order.getUserId(), order.getPrice());
        } catch (Exception e) {
            log.warn("Referral credit failed for order {}: {}", order.getId(), e.getMessage());
        }
    }

    /**
     * For callers that can retry: throws if the reward couldn't be finished. A repeat skips what's done and picks up
     * the reward, because the check below doesn't depend on this call being the one that qualified the friend.
     */
    public void onPaid(Long orderId, Long userId, BigDecimal price) {
        if (price == null || price.signum() <= 0) return;
        Long referrer = requiresNew.execute(status -> qualifyReferral(userId));
        if (referrer != null
                && referralRepository.countByReferrerUserIdAndQualifiedAtIsNotNull(referrer) >= TARGET
                && !referralRewardRepository.existsById(referrer)) {
            grantPremium(referrer);
        }
    }

    /** The referrer of this buyer, or null if she wasn't referred. */
    private Long qualifyReferral(Long buyerUserId) {
        Referral referral = referralRepository.findByReferredUserId(buyerUserId).orElse(null);
        if (referral == null) return null;
        if (referral.getQualifiedAt() == null) {
            referral.setQualifiedAt(LocalDateTime.now());
            referralRepository.save(referral);
            auditService.record("REFERRAL_QUALIFIED", "USER", String.valueOf(referral.getReferrerUserId()), "A referred friend bought a plan");
        }
        return referral.getReferrerUserId();
    }

    /**
     * Asks for the order with no transaction open, so a slow Money API holds no database connection. The key is the
     * order's idempotency key, so a repeat can't create a second free order. The reward row, the sessions and the email
     * then commit together; a concurrent grant hits the referral_rewards primary key and that transaction rolls back.
     */
    private void grantPremium(Long userId) {
        MoneyGateway.RewardOrder reward = moneyGateway.grantReward("referral-reward-" + userId, userId, PlanCode.PREMIUM);
        requiresNew.executeWithoutResult(status -> {
            referralRewardRepository.saveAndFlush(new ReferralReward(userId, reward.orderId()));
            entitlementService.grant(reward.orderId(), userId, reward.planCode(), reward.expiresAt(), reward.sessions());
            auditService.record("REFERRAL_REWARD_GRANTED", "Order", String.valueOf(reward.orderId()), "Free Premium plan for 5 referrals");
            emailReward(userId);
        });
    }

    private void emailReward(Long userId) {
        mailQueue.sendToUser("REFERRAL_REWARD", userId, "You've earned a free NartureBelle Premium plan!",
                "Five friends joined NartureBelle through your invite -- thank you for spreading the care.\n\n"
                        + "Your free Premium plan is active now, with every session it includes. "
                        + "Open NartureBelle to start booking.\n\n-- The NartureBelle team");
    }

    private boolean purchased(Long userId) {
        return moneyGateway.hasPaidOrderAboveZero(userId);
    }

    private String codeFor(Long userId) {
        return referralCodeRepository.findById(userId).map(ReferralCode::getCode).orElseGet(() -> {
            for (int attempt = 0; attempt < 5; attempt++) {
                try {
                    return referralCodeRepository.save(new ReferralCode(userId, randomCode())).getCode();
                } catch (DataIntegrityViolationException collisionOrRace) {
                    var existing = referralCodeRepository.findById(userId);
                    if (existing.isPresent()) return existing.get().getCode();
                }
            }
            throw new IllegalStateException("Could not generate a unique referral code");
        });
    }

    private static String randomCode() {
        StringBuilder sb = new StringBuilder(8);
        for (int i = 0; i < 8; i++) sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        return sb.toString();
    }

    private static String normalise(String code) {
        return code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
    }

    private static ReferralException codeNotFound() {
        return new ReferralException(404, "REFERRAL_CODE_NOT_FOUND", "That invite link isn't valid.");
    }

    /** "Asha R." -- first name and last initial, never the email. */
    private static String displayName(User user) {
        if (user == null || user.getFirstName() == null || user.getFirstName().isBlank()) return "A NartureBelle mom";
        String last = user.getLastName() == null || user.getLastName().isBlank() ? "" : " " + user.getLastName().trim().charAt(0) + ".";
        return user.getFirstName().trim() + last;
    }
}
