package com.narturebelle.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.narturebelle.model.EmploymentType;
import com.narturebelle.model.JobPosting;
import com.narturebelle.model.JobStatus;
import com.narturebelle.model.WorkMode;

import java.time.LocalDateTime;
import java.util.List;

/** applicationCount is admin-only (omitted from the public JSON). */
public record JobResponse(Long id, String title, String team, String location, EmploymentType employmentType,
                          WorkMode workMode, String summary, String description, List<String> requirements,
                          LocalDateTime postedAt, JobStatus status, LocalDateTime closedAt,
                          @JsonInclude(JsonInclude.Include.NON_NULL) Long applicationCount) {

    public static JobResponse from(JobPosting job) {
        return forAdmin(job, null);
    }

    public static JobResponse forAdmin(JobPosting job, Long applicationCount) {
        return new JobResponse(job.getId(), job.getTitle(), job.getTeam(), job.getLocation(), job.getEmploymentType(),
                job.getWorkMode(), job.getSummary(), job.getDescription(), List.copyOf(job.getRequirements()),
                job.getPostedAt(), job.getStatus(), job.getClosedAt(), applicationCount);
    }
}
