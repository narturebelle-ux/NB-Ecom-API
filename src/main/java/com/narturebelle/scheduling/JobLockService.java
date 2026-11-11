package com.narturebelle.scheduling;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Makes a scheduled job run on only one API copy at a time: a row per job holds a lease until a time.
 * Taking it is one conditional UPDATE (or the first INSERT), which the database does atomically, so two
 * copies starting together can't both win. If a copy dies mid-run, the lease simply runs out.
 */
@Component
public class JobLockService {

    private final JdbcTemplate jdbc;

    public JobLockService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** A token if this copy now holds the lock, empty if another copy does. */
    public Optional<String> tryAcquire(String jobName, Duration lease, LocalDateTime now) {
        String token = UUID.randomUUID().toString();
        LocalDateTime until = now.plus(lease);
        int taken = jdbc.update("update job_locks set locked_by = ?, locked_until = ? where name = ? and locked_until < ?",
                token, until, jobName, now);
        if (taken == 1) {
            return Optional.of(token);
        }
        try {
            jdbc.update("insert into job_locks (name, locked_by, locked_until) values (?, ?, ?)", jobName, token, until);
            return Optional.of(token);
        } catch (DataIntegrityViolationException heldByAnotherCopy) {
            return Optional.empty();
        }
    }

    /** Ends the lease early. Only the holder's token can release it. */
    public void release(String jobName, String token, LocalDateTime now) {
        jdbc.update("update job_locks set locked_until = ? where name = ? and locked_by = ?", now, jobName, token);
    }
}
