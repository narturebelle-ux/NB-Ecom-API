package com.narturebelle.dto;

import com.narturebelle.model.EmploymentType;
import com.narturebelle.model.WorkMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Admin create / full replace of a job posting. */
public class JobRequest {

    @NotBlank @Size(max = 150) private String title;
    @Size(max = 100) private String team;
    @NotBlank @Size(max = 120) private String location;
    @NotNull private EmploymentType employmentType;
    @NotNull private WorkMode workMode;
    @NotBlank @Size(max = 500) private String summary;
    @NotBlank @Size(max = 10000) private String description;
    @Size(max = 30) private List<@NotBlank @Size(max = 500) String> requirements;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getTeam() { return team; }
    public void setTeam(String team) { this.team = team; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public EmploymentType getEmploymentType() { return employmentType; }
    public void setEmploymentType(EmploymentType employmentType) { this.employmentType = employmentType; }
    public WorkMode getWorkMode() { return workMode; }
    public void setWorkMode(WorkMode workMode) { this.workMode = workMode; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public List<String> getRequirements() { return requirements; }
    public void setRequirements(List<String> requirements) { this.requirements = requirements; }
}
