package com.narturebelle.model;

import jakarta.persistence.*;

/** One dose in a schedule version. dueAgeDays is the source of truth for the due date; dueAgeLabel
 * is display copy ("6 weeks", "9 months") so the client never has to round days into months. */
@Entity
@Table(name = "vaccine_schedule_items")
public class VaccineScheduleItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long scheduleVersionId;

    @Column(nullable = false)
    private String vaccineName;

    @Column(nullable = false)
    private Integer dueAgeDays;

    @Column(nullable = false)
    private String dueAgeLabel;

    @Column(nullable = false)
    private Integer sortOrder;

    public VaccineScheduleItem() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getScheduleVersionId() { return scheduleVersionId; }
    public void setScheduleVersionId(Long scheduleVersionId) { this.scheduleVersionId = scheduleVersionId; }

    public String getVaccineName() { return vaccineName; }
    public void setVaccineName(String vaccineName) { this.vaccineName = vaccineName; }

    public Integer getDueAgeDays() { return dueAgeDays; }
    public void setDueAgeDays(Integer dueAgeDays) { this.dueAgeDays = dueAgeDays; }

    public String getDueAgeLabel() { return dueAgeLabel; }
    public void setDueAgeLabel(String dueAgeLabel) { this.dueAgeLabel = dueAgeLabel; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
}
