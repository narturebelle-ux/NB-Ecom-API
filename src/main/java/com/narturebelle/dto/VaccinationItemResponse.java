package com.narturebelle.dto;

import com.narturebelle.model.ChildVaccination;
import com.narturebelle.model.VaccinationStatus;
import com.narturebelle.model.VaccineScheduleItem;

import java.time.LocalDate;

public class VaccinationItemResponse {

    /** A dose due within this many days counts as DUE rather than UPCOMING. Display grouping only, not clinical guidance. */
    static final int DUE_WINDOW_DAYS = 30;

    private Long scheduleItemId;
    private String vaccineName;
    private Integer dueAgeDays;
    private String dueAgeLabel;
    private LocalDate dueDate;
    private VaccinationStatus status;
    private LocalDate givenAt;
    private String givenWhere;

    public static VaccinationItemResponse from(VaccineScheduleItem item, LocalDate dateOfBirth,
                                               ChildVaccination given, LocalDate today) {
        VaccinationItemResponse dto = new VaccinationItemResponse();
        dto.scheduleItemId = item.getId();
        dto.vaccineName = item.getVaccineName();
        dto.dueAgeDays = item.getDueAgeDays();
        dto.dueAgeLabel = item.getDueAgeLabel();
        dto.dueDate = dateOfBirth.plusDays(item.getDueAgeDays());
        if (given != null) {
            dto.status = VaccinationStatus.DONE;
            dto.givenAt = given.getGivenAt();
            dto.givenWhere = given.getGivenWhere();
        } else if (dto.dueDate.isBefore(today)) {
            dto.status = VaccinationStatus.OVERDUE;
        } else if (!dto.dueDate.isAfter(today.plusDays(DUE_WINDOW_DAYS))) {
            dto.status = VaccinationStatus.DUE;
        } else {
            dto.status = VaccinationStatus.UPCOMING;
        }
        return dto;
    }

    public Long getScheduleItemId() { return scheduleItemId; }
    public String getVaccineName() { return vaccineName; }
    public Integer getDueAgeDays() { return dueAgeDays; }
    public String getDueAgeLabel() { return dueAgeLabel; }
    public LocalDate getDueDate() { return dueDate; }
    public VaccinationStatus getStatus() { return status; }
    public LocalDate getGivenAt() { return givenAt; }
    public String getGivenWhere() { return givenWhere; }
}
