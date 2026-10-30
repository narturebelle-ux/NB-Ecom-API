package com.narturebelle.repository;

import com.narturebelle.model.JobPosting;
import com.narturebelle.model.JobStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JobPostingRepository extends JpaRepository<JobPosting, Long> {
    List<JobPosting> findByStatusOrderByPostedAtDesc(JobStatus status);
    List<JobPosting> findAllByOrderByPostedAtDesc();
}
