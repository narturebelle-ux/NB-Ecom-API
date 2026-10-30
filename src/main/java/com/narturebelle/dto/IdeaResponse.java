package com.narturebelle.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.narturebelle.model.Idea;
import com.narturebelle.model.IdeaCategory;
import com.narturebelle.model.IdeaStatus;

import java.time.LocalDateTime;

/**
 * One idea as the pool shows it. authorName is first name + last initial (e.g. "Asha R."). paidVotes counts
 * toward voteGoal; votes includes everyone. votedByMe / mine are null for signed-out callers.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record IdeaResponse(Long id, String number, IdeaCategory category, String categoryLabel, String title,
                           String description, String authorName, IdeaStatus status, long votes, long paidVotes,
                           int voteGoal, boolean goalReached, String impactNote, LocalDateTime implementedAt,
                           LocalDateTime createdAt, Boolean votedByMe, Boolean mine, Boolean featureConsent) {

    public static IdeaResponse of(Idea idea, String authorName, long votes, long paidVotes, int voteGoal,
                                  Boolean votedByMe, Boolean mine) {
        return new IdeaResponse(idea.getId(), idea.number(), idea.getCategory(), idea.getCategory().getLabel(),
                idea.getTitle(), idea.getDescription(), authorName, idea.getStatus(), votes, paidVotes, voteGoal,
                paidVotes >= voteGoal, idea.getImpactNote(), idea.getImplementedAt(), idea.getCreatedAt(),
                votedByMe, mine, Boolean.TRUE.equals(mine) ? idea.isFeatureConsent() : null);
    }
}
