package com.narturebelle.model;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** A dose recorded as given. Only "done" is stored -- due/overdue/upcoming are computed. One row per
 * (child, schedule item), enforced by a DB unique constraint as the backstop to the service's upsert. */
@Entity
@Table(name = "child_vaccinations", uniqueConstraints = @UniqueConstraint(columnNames = {"child_id", "schedule_item_id"}))
public class ChildVaccination {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long childId;

    @Column(nullable = false)
    private Long scheduleItemId;

    @Column(nullable = false)
    private LocalDate givenAt;

    private String givenWhere;

    @Column(nullable = false)
    private LocalDateTime recordedAt;

    @PrePersist
    @PreUpdate
    protected void onSave() {
        this.recordedAt = LocalDateTime.now();
    }

    public ChildVaccination() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getChildId() { return childId; }
    public void setChildId(Long childId) { this.childId = childId; }

    public Long getScheduleItemId() { return scheduleItemId; }
    public void setScheduleItemId(Long scheduleItemId) { this.scheduleItemId = scheduleItemId; }

    public LocalDate getGivenAt() { return givenAt; }
    public void setGivenAt(LocalDate givenAt) { this.givenAt = givenAt; }

    public String getGivenWhere() { return givenWhere; }
    public void setGivenWhere(String givenWhere) { this.givenWhere = givenWhere; }

    public LocalDateTime getRecordedAt() { return recordedAt; }
    public void setRecordedAt(LocalDateTime recordedAt) { this.recordedAt = recordedAt; }
}
