package com.narturebelle.repository;

import com.narturebelle.model.ChecklistTask;
import com.narturebelle.model.PregnancyStage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChecklistTaskRepository extends JpaRepository<ChecklistTask, Long> {
    List<ChecklistTask> findByStage(PregnancyStage stage);
}
