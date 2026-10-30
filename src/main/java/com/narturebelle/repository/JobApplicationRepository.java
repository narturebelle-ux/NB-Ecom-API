package com.narturebelle.repository;

import com.narturebelle.model.JobApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface JobApplicationRepository extends JpaRepository<JobApplication, Long> {
    List<JobApplication> findByJobIdOrderBySubmittedAtDesc(Long jobId);

    /** [jobId, count] pairs, for the admin job list. */
    @Query("select a.jobId, count(a) from JobApplication a group by a.jobId")
    List<Object[]> countByJob();
}
