package com.narturebelle.scheduling;

import com.narturebelle.service.VideoRetentionService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Daily at 09:00 India time, only where SCHEDULING_ENABLED is on, and on one API copy at a time (job lock).
 * Containers that sleep may miss it, so the external scheduler (D18) can also call
 * POST /internal/v1/jobs/video-retention; a late or repeated run is safe.
 */
@Component
@ConditionalOnProperty(prefix = "narturebelle.scheduling", name = "enabled", matchIfMissing = true)
public class VideoRetentionJob {

    private final VideoRetentionService videoRetentionService;

    public VideoRetentionJob(VideoRetentionService videoRetentionService) {
        this.videoRetentionService = videoRetentionService;
    }

    @Scheduled(cron = "${records.video.job-cron:0 0 9 * * *}", zone = "Asia/Kolkata")
    public void runDaily() {
        videoRetentionService.runIfFree(LocalDateTime.now());
    }
}
