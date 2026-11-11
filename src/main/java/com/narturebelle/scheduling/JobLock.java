package com.narturebelle.scheduling;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** One lease row per scheduled job; see JobLockService. Mapped only so the test schema has the table. */
@Entity
@Table(name = "job_locks")
public class JobLock {

    @Id
    @Column(length = 64)
    private String name;

    @Column(length = 64)
    private String lockedBy;

    @Column(nullable = false)
    private LocalDateTime lockedUntil;

    protected JobLock() {}

    public String getName() { return name; }
    public String getLockedBy() { return lockedBy; }
    public LocalDateTime getLockedUntil() { return lockedUntil; }
}
