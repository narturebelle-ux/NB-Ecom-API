package com.narturebelle.providers.repository;

import com.narturebelle.providers.model.ApplicationAnswer;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface ApplicationAnswerRepository extends JpaRepository<ApplicationAnswer, Long> {

    List<ApplicationAnswer> findByApplicationId(Long applicationId);

    Optional<ApplicationAnswer> findByApplicationIdAndQuestionKey(Long applicationId, String questionKey);

    @Transactional
    void deleteByApplicationId(Long applicationId);
}
