package com.narturebelle.service;

import com.narturebelle.dto.CreateIdeaRequest;
import com.narturebelle.dto.IdeaResponse;
import com.narturebelle.dto.IdeaStatusRequest;
import com.narturebelle.email.EmailMessage;
import com.narturebelle.email.EmailSender;
import com.narturebelle.exception.IdeaException;
import com.narturebelle.model.FeatureFlag;
import com.narturebelle.model.Idea;
import com.narturebelle.model.IdeaCategory;
import com.narturebelle.model.IdeaStatus;
import com.narturebelle.model.IdeaVote;
import com.narturebelle.model.User;
import com.narturebelle.repository.FeatureFlagRepository;
import com.narturebelle.repository.IdeaRepository;
import com.narturebelle.repository.IdeaVoteRepository;
import com.narturebelle.repository.UserRepository;
import com.narturebelle.security.AuditService;
import com.narturebelle.security.RateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The idea pool: members post ideas in five categories and vote on each other's. Only votes from members
 * with a paid order count toward the goal (product-owner decision, 2026-10-30: 1,000 paid votes), which
 * is what makes sharing an idea with friends worthwhile. Implemented ideas feature on Idea Impact.
 * The pool runs for a limited time: the ideaPool feature flag closes submissions and voting.
 */
@Service
public class IdeaService {

    private static final Logger log = LoggerFactory.getLogger(IdeaService.class);
    static final String POOL_FLAG = "ideaPool";
    private static final List<IdeaStatus> VISIBLE = List.of(IdeaStatus.OPEN, IdeaStatus.UNDER_REVIEW, IdeaStatus.IMPLEMENTED);

    private final IdeaRepository ideaRepository;
    private final IdeaVoteRepository ideaVoteRepository;
    private final UserRepository userRepository;
    private final FeatureFlagRepository featureFlagRepository;
    private final RateLimiter rateLimiter;
    private final EmailSender emailSender;
    private final AuditService auditService;
    private final int voteGoal;
    private final String adminEmail;

    public IdeaService(IdeaRepository ideaRepository, IdeaVoteRepository ideaVoteRepository, UserRepository userRepository,
                       FeatureFlagRepository featureFlagRepository, RateLimiter rateLimiter, EmailSender emailSender,
                       AuditService auditService, @Value("${ideas.vote-goal}") int voteGoal,
                       @Value("${careers.admin-email}") String adminEmail) {
        this.ideaRepository = ideaRepository;
        this.ideaVoteRepository = ideaVoteRepository;
        this.userRepository = userRepository;
        this.featureFlagRepository = featureFlagRepository;
        this.rateLimiter = rateLimiter;
        this.emailSender = emailSender;
        this.auditService = auditService;
        this.voteGoal = voteGoal;
        this.adminEmail = adminEmail;
    }

    public boolean poolOpen() {
        return featureFlagRepository.findById(POOL_FLAG).map(FeatureFlag::isEnabled).orElse(false);
    }

    public int voteGoal() {
        return voteGoal;
    }

    // ---- public ----

    /** sort: "top" (paid votes, then all votes) or "new". */
    public List<IdeaResponse> pool(IdeaCategory category, String sort, Long viewerId) {
        List<Idea> ideas = ideaRepository.findByStatusInOrderByCreatedAtDesc(VISIBLE).stream()
                .filter(idea -> category == null || idea.getCategory() == category)
                .toList();
        List<IdeaResponse> responses = toResponses(ideas, viewerId, true);
        if (!"new".equalsIgnoreCase(sort)) {
            responses = responses.stream()
                    .sorted(Comparator.comparingLong(IdeaResponse::paidVotes).reversed()
                            .thenComparing(Comparator.comparingLong(IdeaResponse::votes).reversed()))
                    .toList();
        }
        return responses;
    }

    public IdeaResponse get(Long id, Long viewerId) {
        Idea idea = ideaRepository.findById(id)
                .filter(i -> i.getStatus() != IdeaStatus.DECLINED || i.getUserId().equals(viewerId))
                .orElseThrow(() -> notFound(id));
        return toResponses(List.of(idea), viewerId, true).get(0);
    }

    /** Implemented ideas, newest first. The author is named only if she agreed to be featured. */
    public List<IdeaResponse> impact() {
        return toResponses(ideaRepository.findByStatusOrderByImplementedAtDesc(IdeaStatus.IMPLEMENTED), null, false);
    }

    // ---- members ----

    public IdeaResponse submit(Long userId, CreateIdeaRequest req) {
        requireOpenPool();
        if (!rateLimiter.tryAcquire("idea-user:" + userId, 5, Duration.ofDays(1))) {
            throw new IdeaException(429, "TOO_MANY_REQUESTS", "You can share up to 5 ideas a day. Please come back tomorrow.");
        }
        Idea idea = new Idea();
        idea.setUserId(userId);
        idea.setCategory(req.getCategory());
        idea.setTitle(req.getTitle().trim());
        idea.setDescription(req.getDescription().strip());
        idea.setFeatureConsent(req.isFeatureConsent());
        idea.setStatus(IdeaStatus.OPEN);
        Idea saved = ideaRepository.save(idea);
        auditService.record("IDEA_SUBMITTED", "IDEA", String.valueOf(saved.getId()), "Member shared an idea");
        sendQuietly(new EmailMessage(adminEmail, null, "New idea " + saved.number() + ": " + CareersService.oneLine(saved.getTitle()),
                saved.getCategory().getLabel() + "\n\n" + saved.getTitle() + "\n\n" + saved.getDescription()
                        + "\n\nMember id " + userId + ". Manage it in the admin ideas screen.", null));
        return toResponses(List.of(saved), userId, true).get(0);
    }

    public List<IdeaResponse> mine(Long userId) {
        return toResponses(ideaRepository.findByUserIdOrderByCreatedAtDesc(userId), userId, true);
    }

    public IdeaResponse vote(Long ideaId, Long userId) {
        requireOpenPool();
        Idea idea = votable(ideaId);
        if (idea.getUserId().equals(userId)) {
            throw new IdeaException(409, "IDEA_SELF_VOTE", "You can't vote for your own idea -- share it so others can!");
        }
        if (ideaVoteRepository.findByIdeaIdAndUserId(ideaId, userId).isEmpty()) {
            try {
                ideaVoteRepository.save(new IdeaVote(ideaId, userId));
            } catch (DataIntegrityViolationException alreadyVoted) {
                // A double-click raced the check above; the unique (idea_id, user_id) constraint kept one vote.
            }
        }
        IdeaResponse response = toResponses(List.of(idea), userId, true).get(0);
        if (response.goalReached() && idea.getGoalReachedAt() == null) {
            idea.setGoalReachedAt(LocalDateTime.now());
            ideaRepository.save(idea);
            auditService.record("IDEA_GOAL_REACHED", "IDEA", String.valueOf(ideaId), "Idea reached its paid-vote goal");
            sendQuietly(new EmailMessage(adminEmail, null, "Idea " + idea.number() + " reached " + voteGoal + " paid votes",
                    idea.getTitle() + "\n\n" + idea.getDescription() + "\n\nIt's ready for review in the admin ideas screen.", null));
        }
        return response;
    }

    public IdeaResponse unvote(Long ideaId, Long userId) {
        requireOpenPool();
        Idea idea = votable(ideaId);
        ideaVoteRepository.findByIdeaIdAndUserId(ideaId, userId).ifPresent(ideaVoteRepository::delete);
        return toResponses(List.of(idea), userId, true).get(0);
    }

    // ---- admin ----

    public List<IdeaResponse> all(IdeaStatus status) {
        List<Idea> ideas = ideaRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(i -> status == null || i.getStatus() == status)
                .toList();
        return toResponses(ideas, null, true);
    }

    public IdeaResponse setStatus(Long id, IdeaStatusRequest req) {
        Idea idea = ideaRepository.findById(id).orElseThrow(() -> notFound(id));
        String note = req.getImpactNote() == null || req.getImpactNote().isBlank() ? null : req.getImpactNote().strip();
        if (req.getStatus() == IdeaStatus.IMPLEMENTED && note == null && idea.getImpactNote() == null) {
            throw new IdeaException(400, "IMPACT_NOTE_REQUIRED", "Say what changed -- it's shown on the Idea Impact section.");
        }
        idea.setStatus(req.getStatus());
        if (note != null) idea.setImpactNote(note);
        idea.setImplementedAt(req.getStatus() == IdeaStatus.IMPLEMENTED ? LocalDateTime.now() : null);
        Idea saved = ideaRepository.save(idea);
        auditService.record("IDEA_STATUS_CHANGED", "IDEA", String.valueOf(id), "Admin set idea status to " + req.getStatus());
        return toResponses(List.of(saved), null, true).get(0);
    }

    // ---- helpers ----

    private void requireOpenPool() {
        if (!poolOpen()) {
            throw new IdeaException(409, "IDEA_POOL_CLOSED", "The idea pool is closed right now. Thank you for every idea!");
        }
    }

    private Idea votable(Long ideaId) {
        Idea idea = ideaRepository.findById(ideaId)
                .filter(i -> i.getStatus() != IdeaStatus.DECLINED)
                .orElseThrow(() -> notFound(ideaId));
        if (idea.getStatus() != IdeaStatus.OPEN) {
            throw new IdeaException(409, "IDEA_NOT_OPEN", "Voting on this idea has finished.");
        }
        return idea;
    }

    private static IdeaException notFound(Long id) {
        return new IdeaException(404, "IDEA_NOT_FOUND", "Idea " + id + " not found.");
    }

    /** alwaysNameAuthor=false (Idea Impact) names only authors who agreed to be featured. */
    private List<IdeaResponse> toResponses(List<Idea> ideas, Long viewerId, boolean alwaysNameAuthor) {
        if (ideas.isEmpty()) return List.of();
        List<Long> ids = ideas.stream().map(Idea::getId).toList();
        Map<Long, long[]> counts = new HashMap<>();
        for (Object[] row : ideaVoteRepository.countVotes(ids)) {
            counts.put(((Number) row[0]).longValue(), new long[]{((Number) row[1]).longValue(), ((Number) row[2]).longValue()});
        }
        Map<Long, User> authors = userRepository.findAllById(ideas.stream().map(Idea::getUserId).distinct().toList()).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        Set<Long> votedByViewer = viewerId == null ? Set.of()
                : ideaVoteRepository.findByUserIdAndIdeaIdIn(viewerId, ids).stream().map(IdeaVote::getIdeaId).collect(Collectors.toSet());
        return ideas.stream().map(idea -> {
            long[] c = counts.getOrDefault(idea.getId(), new long[]{0, 0});
            boolean named = alwaysNameAuthor || idea.isFeatureConsent();
            String author = named ? displayName(authors.get(idea.getUserId())) : "A NartureBelle mom";
            Boolean voted = viewerId == null ? null : votedByViewer.contains(idea.getId());
            Boolean mine = viewerId == null ? null : idea.getUserId().equals(viewerId);
            return IdeaResponse.of(idea, author, c[0], c[1], voteGoal, voted, mine);
        }).toList();
    }

    /** "Asha R." -- first name and last initial, never the email. */
    static String displayName(User user) {
        if (user == null || user.getFirstName() == null || user.getFirstName().isBlank()) return "A NartureBelle mom";
        String last = user.getLastName() == null || user.getLastName().isBlank() ? "" : " " + user.getLastName().trim().charAt(0) + ".";
        return user.getFirstName().trim() + last;
    }

    private void sendQuietly(EmailMessage message) {
        try {
            emailSender.send(message);
        } catch (Exception e) {
            log.warn("Idea email failed ({}): {}", message.subject(), e.getMessage());
        }
    }
}
