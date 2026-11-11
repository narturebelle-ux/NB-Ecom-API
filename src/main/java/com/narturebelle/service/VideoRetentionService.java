package com.narturebelle.service;

import com.narturebelle.mail.DueReminder;
import com.narturebelle.mail.MailQueue;
import com.narturebelle.mail.MailsClient;
import com.narturebelle.model.HealthRecord;
import com.narturebelle.model.VideoRetentionNotice;
import com.narturebelle.repository.HealthRecordRepository;
import com.narturebelle.repository.VideoRetentionNoticeRepository;
import com.narturebelle.scheduling.JobLockService;
import com.narturebelle.storage.ScanPolicy;
import com.narturebelle.security.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The daily video job (F9-S19 items 12 and 13, D49). It deletes nothing: S3 lifecycle rules move videos
 * to trash and delete them. This job:
 * - warns the mother before removal (day 23 and day 29 by default), by email through the outbox to
 *   NB-Ecom-Notifications-API, with a link to the app, never to S3;
 * - writes RECORD_VIDEO_TRASHED at the removal date and RECORD_VIDEO_PURGED when the trash period ends.
 * Each happens once per video per removal date (video_retention_notices' unique key), so reruns, late runs
 * and two API copies running together are all safe. Logs and audit hold IDs and dates only, no health details.
 */
@Service
public class VideoRetentionService {

    public static final String JOB_NAME = "video-retention";
    static final Duration LEASE = Duration.ofMinutes(30);
    /** If the job was down, it still catches up on videos removed within this many days before the trash ended. */
    static final int CATCH_UP_DAYS = 30;
    static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);

    private static final Logger log = LoggerFactory.getLogger(VideoRetentionService.class);

    private final HealthRecordRepository healthRecordRepository;
    private final VideoRetentionNoticeRepository noticeRepository;
    private final RecordFileRules rules;
    private final AuditService auditService;
    private final MailQueue mailQueue;
    private final MailsClient mailsClient;
    private final JobLockService jobLockService;
    private final ScanPolicy scanPolicy;
    private final TransactionTemplate requiresNew;
    private final String recordsUrl;
    private final boolean inAppWarnings;

    public VideoRetentionService(HealthRecordRepository healthRecordRepository, VideoRetentionNoticeRepository noticeRepository,
                                 RecordFileRules rules, AuditService auditService, MailQueue mailQueue, MailsClient mailsClient,
                                 JobLockService jobLockService, PlatformTransactionManager transactionManager,
                                 ScanPolicy scanPolicy,
                                 @Value("${records.video.app-records-url:https://narturebelle.com/records}") String recordsUrl,
                                 @Value("${records.video.in-app-warnings-enabled:false}") boolean inAppWarnings) {
        this.healthRecordRepository = healthRecordRepository;
        this.noticeRepository = noticeRepository;
        this.rules = rules;
        this.auditService = auditService;
        this.mailQueue = mailQueue;
        this.mailsClient = mailsClient;
        this.jobLockService = jobLockService;
        this.scanPolicy = scanPolicy;
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.recordsUrl = recordsUrl;
        this.inAppWarnings = inAppWarnings;
    }

    /** Runs only if no other API copy is running it. Empty when another copy holds the lock. */
    public Optional<Result> runIfFree(LocalDateTime now) {
        Optional<String> token = jobLockService.tryAcquire(JOB_NAME, LEASE, now);
        if (token.isEmpty()) {
            log.info("Video job skipped: another copy is running it");
            return Optional.empty();
        }
        try {
            return Optional.of(run(now));
        } finally {
            jobLockService.release(JOB_NAME, token.get(), LocalDateTime.now());
        }
    }

    /** One pass. Safe to repeat: anything already done for a video's current removal date is skipped. */
    public Result run(LocalDateTime now) {
        int trashDays = rules.trashDays();
        List<Integer> warnDays = rules.warnDaysBefore();
        int earliestWarning = warnDays.isEmpty() ? 0 : warnDays.get(0);
        List<HealthRecord> videos = healthRecordRepository.findVideosWithRemovalBetween(
                now.minusDays(trashDays + CATCH_UP_DAYS), now.plusDays(earliestWarning));

        int warnings = 0, trashed = 0, purged = 0;
        List<DueReminder> inApp = new ArrayList<>();
        for (HealthRecord video : videos) {
            LocalDateTime removalAt = video.getRemovalAt();
            LocalDateTime purgeAt = removalAt.plusDays(trashDays);
            if (!now.isBefore(removalAt)) {
                if (once(video, VideoRetentionNotice.TRASHED, now, () -> auditService.record("RECORD_VIDEO_TRASHED",
                        "HealthRecord", String.valueOf(video.getId()), null,
                        Map.of("removalAt", removalAt.toString(), "permanentDeletionAt", purgeAt.toString())))) {
                    trashed++;
                }
                if (!now.isBefore(purgeAt) && once(video, VideoRetentionNotice.PURGED, now, () -> auditService.record(
                        "RECORD_VIDEO_PURGED", "HealthRecord", String.valueOf(video.getId()), null,
                        Map.of("permanentDeletionAt", purgeAt.toString())))) {
                    purged++;
                }
                continue;
            }
            Integer due = dueWarning(warnDays, removalAt, now);
            // Only a video she can actually download gets a "download it now" warning.
            if (due == null || !scanPolicy.allowsDownload(video)) {
                continue;
            }
            if (once(video, VideoRetentionNotice.warning(due), now, () -> warn(video, due, now))) {
                warnings++;
                inApp.add(new DueReminder(video.getUserId(), "VIDEO_REMOVAL_WARNING", "HEALTH_RECORD", video.getId(),
                        removalAt, "warn-" + due));
            }
        }
        sendInApp(inApp);
        Result result = new Result(videos.size(), warnings, trashed, purged);
        log.info("Video job: checked {}, warned {}, trashed {}, purged {}", result.checked(), warnings, trashed, purged);
        return result;
    }

    /**
     * The most urgent warning that's due, e.g. on day 29 the 1-day one, not the 7-day one. A missed earlier
     * warning isn't sent late on top of it, so she gets one message, with the right number of days left.
     */
    static Integer dueWarning(List<Integer> warnDaysLargestFirst, LocalDateTime removalAt, LocalDateTime now) {
        Integer due = null;
        for (Integer days : warnDaysLargestFirst) {
            if (!now.isBefore(removalAt.minusDays(days))) {
                due = days;
            }
        }
        return due;
    }

    /** Records the notice and its side effects in one transaction. False if it was already done. */
    private boolean once(HealthRecord video, String kind, LocalDateTime now, Runnable sideEffects) {
        if (noticeRepository.existsByRecordIdAndKindAndCycleRemovalAt(video.getId(), kind, video.getRemovalAt())) {
            return false;
        }
        try {
            requiresNew.executeWithoutResult(status -> {
                noticeRepository.saveAndFlush(new VideoRetentionNotice(video.getId(), kind, video.getRemovalAt(), now));
                sideEffects.run();
            });
            return true;
        } catch (DataIntegrityViolationException doneByAnotherCopy) {
            return false;
        } catch (DataAccessException e) {
            // Rolled back, so nothing half-done: the next run tries this one again.
            log.warn("Video job skipped record {} this run: {}", video.getId(), e.getClass().getSimpleName());
            return false;
        }
    }

    private void warn(HealthRecord video, int warningDays, LocalDateTime now) {
        String date = indianDate(video.getRemovalAt());
        String when = whenText(video.getRemovalAt(), now);
        String link = recordsUrl + "?video=" + video.getId();
        String subject = "Your video will be removed on " + date;
        String text = "Hello,\n\n"
                + "One of the videos you saved with NartureBelle™ will be removed " + when + ", on " + date + ". "
                + "After that it can't be downloaded.\n\n"
                + "Download it now: " + link + "\n\n"
                + "You'll be asked to sign in first.\n\n"
                + "The NartureBelle™ team";
        mailQueue.sendToUserOnce(eventId(video, warningDays), "VIDEO_REMOVAL_WARNING", video.getUserId(), subject, text);
        auditService.record("RECORD_VIDEO_WARNING_SENT", "HealthRecord", String.valueOf(video.getId()), null,
                Map.of("daysBefore", warningDays, "removalAt", video.getRemovalAt().toString()));
    }

    /** Fixed per video, removal date and warning, so neither the outbox nor Notifications sends it twice. */
    static String eventId(HealthRecord video, int warningDays) {
        long removalEpoch = video.getRemovalAt().atZone(ZoneId.systemDefault()).toEpochSecond();
        return "care-video-warning-" + video.getId() + "-" + removalEpoch + "-" + warningDays;
    }

    /** Removal dates are midnight UTC, which is 05:30 the same day in India: show the Indian date. */
    static String indianDate(LocalDateTime serverTime) {
        return serverTime.atZone(ZoneId.systemDefault()).withZoneSameInstant(INDIA).format(DATE);
    }

    static String whenText(LocalDateTime removalAt, LocalDateTime now) {
        LocalDate removalDay = removalAt.atZone(ZoneId.systemDefault()).withZoneSameInstant(INDIA).toLocalDate();
        LocalDate today = now.atZone(ZoneId.systemDefault()).withZoneSameInstant(INDIA).toLocalDate();
        long days = ChronoUnit.DAYS.between(today, removalDay);
        if (days <= 0) return "today";
        if (days == 1) return "tomorrow";
        return "in " + days + " days";
    }

    /**
     * In-app notices need NB-Ecom-Notifications-API to accept reminder type VIDEO_REMOVAL_WARNING and source
     * type HEALTH_RECORD (TASKS.md F9-S19 item 12), so they're off until then. The UI already shows its own
     * banner from warnFrom. Best effort: the email and audit are already saved.
     */
    private void sendInApp(List<DueReminder> reminders) {
        if (!inAppWarnings || reminders.isEmpty()) {
            return;
        }
        try {
            mailsClient.sendReminders(reminders);
        } catch (Exception e) {
            log.warn("In-app video warnings not sent: {}", e.getClass().getSimpleName());
        }
    }

    public record Result(int checked, int warningsSent, int trashed, int purged) {}
}
