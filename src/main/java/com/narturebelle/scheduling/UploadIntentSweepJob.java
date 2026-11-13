package com.narturebelle.scheduling;

import com.narturebelle.storage.DirectUploadService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Hourly, where SCHEDULING_ENABLED is on: removes provider and careers uploads that were never saved
 * (a day after their link expired), and their S3 files. Without it an abandoned public careers upload
 * would stay in files/ for good, since documents have no expiry rule. Safe to run on two copies at once.
 */
@Component
@ConditionalOnProperty(prefix = "narturebelle.scheduling", name = "enabled", matchIfMissing = true)
public class UploadIntentSweepJob {

    private static final Logger log = LoggerFactory.getLogger(UploadIntentSweepJob.class);

    private final DirectUploadService directUploadService;

    public UploadIntentSweepJob(DirectUploadService directUploadService) {
        this.directUploadService = directUploadService;
    }

    @Scheduled(fixedRate = 3_600_000, initialDelay = 300_000)
    public void sweep() {
        try {
            int removed = directUploadService.sweepStale(LocalDateTime.now());
            if (removed > 0) {
                log.info("Removed {} unsaved provider/careers uploads", removed);
            }
        } catch (Exception e) {
            log.warn("Upload sweep failed: {}", e.getClass().getSimpleName());
        }
    }
}
