package com.narturebelle.repository;

import com.narturebelle.model.FeedbackMessage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeedbackMessageRepository extends JpaRepository<FeedbackMessage, Long> {
}
