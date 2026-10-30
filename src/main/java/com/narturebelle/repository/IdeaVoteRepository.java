package com.narturebelle.repository;

import com.narturebelle.model.IdeaVote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface IdeaVoteRepository extends JpaRepository<IdeaVote, Long> {

    Optional<IdeaVote> findByIdeaIdAndUserId(Long ideaId, Long userId);

    List<IdeaVote> findByUserIdAndIdeaIdIn(Long userId, Collection<Long> ideaIds);

    /** [ideaId, all votes, paid votes] for the given ideas. A vote is "paid" while the voter has any PAID order. */
    @Query(value = "select v.idea_id, count(*), "
            + "sum(case when exists (select 1 from orders o where o.user_id = v.user_id and o.status = 'PAID') then 1 else 0 end) "
            + "from idea_votes v where v.idea_id in (:ideaIds) group by v.idea_id", nativeQuery = true)
    List<Object[]> countVotes(Collection<Long> ideaIds);
}
