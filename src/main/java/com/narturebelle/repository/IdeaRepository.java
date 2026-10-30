package com.narturebelle.repository;

import com.narturebelle.model.Idea;
import com.narturebelle.model.IdeaStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface IdeaRepository extends JpaRepository<Idea, Long> {
    List<Idea> findByStatusInOrderByCreatedAtDesc(Collection<IdeaStatus> statuses);
    List<Idea> findByStatusOrderByImplementedAtDesc(IdeaStatus status);
    List<Idea> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<Idea> findAllByOrderByCreatedAtDesc();
}
