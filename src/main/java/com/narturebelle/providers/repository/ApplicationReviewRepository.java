package com.narturebelle.providers.repository;

import com.narturebelle.providers.model.ApplicationReview;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationReviewRepository extends JpaRepository<ApplicationReview, Long> {

    List<ApplicationReview> findByApplicationIdOrderByCreatedAtAsc(Long applicationId);
}
