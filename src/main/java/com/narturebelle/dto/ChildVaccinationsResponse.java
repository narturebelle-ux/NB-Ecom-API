package com.narturebelle.dto;

import com.narturebelle.model.ReviewStatus;
import com.narturebelle.model.VaccineScheduleVersion;

import java.util.List;

public class ChildVaccinationsResponse {

    private Long childId;
    private Schedule schedule;
    private List<VaccinationItemResponse> items;

    /** schedule is null (and items empty) when no schedule version is active. */
    public static ChildVaccinationsResponse of(Long childId, VaccineScheduleVersion version, List<VaccinationItemResponse> items) {
        ChildVaccinationsResponse dto = new ChildVaccinationsResponse();
        dto.childId = childId;
        dto.schedule = version != null ? Schedule.from(version) : null;
        dto.items = items;
        return dto;
    }

    public Long getChildId() { return childId; }
    public Schedule getSchedule() { return schedule; }
    public List<VaccinationItemResponse> getItems() { return items; }

    public static class Schedule {
        private Long id;
        private Integer version;
        private String title;
        private ReviewStatus reviewStatus;

        static Schedule from(VaccineScheduleVersion version) {
            Schedule dto = new Schedule();
            dto.id = version.getId();
            dto.version = version.getVersion();
            dto.title = version.getTitle();
            dto.reviewStatus = version.getReviewStatus();
            return dto;
        }

        public Long getId() { return id; }
        public Integer getVersion() { return version; }
        public String getTitle() { return title; }
        public ReviewStatus getReviewStatus() { return reviewStatus; }
    }
}
