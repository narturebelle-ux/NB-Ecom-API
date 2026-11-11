package com.narturebelle.service;

import com.narturebelle.mail.MailsClient;
import com.narturebelle.mail.OutgoingEmailRepository;
import com.narturebelle.mail.OutgoingMail;
import com.narturebelle.model.AuditEvent;
import com.narturebelle.model.FileKind;
import com.narturebelle.model.HealthRecord;
import com.narturebelle.model.RecordCategory;
import com.narturebelle.model.RecordStatus;
import com.narturebelle.model.Role;
import com.narturebelle.model.ScanVerdict;
import com.narturebelle.model.User;
import com.narturebelle.model.VideoRetentionNotice;
import com.narturebelle.repository.AuditEventRepository;
import com.narturebelle.repository.HealthRecordRepository;
import com.narturebelle.repository.UserRepository;
import com.narturebelle.repository.VideoRetentionNoticeRepository;
import com.narturebelle.scheduling.JobLockService;
import com.narturebelle.storage.StorageBackend;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** F9-S19 items 12 and 13: warnings on day 23 and 29, TRASHED/PURGED audit, once only, one copy at a time. */
@SpringBootTest
@ActiveProfiles("test")
class VideoRetentionServiceTest {

    @Autowired private VideoRetentionService service;
    @Autowired private JobLockService jobLockService;
    @Autowired private HealthRecordRepository healthRecordRepository;
    @Autowired private VideoRetentionNoticeRepository noticeRepository;
    @Autowired private OutgoingEmailRepository outgoingEmailRepository;
    @Autowired private AuditEventRepository auditEventRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RecordFileRules rules;
    @Autowired private JdbcTemplate jdbc;

    @MockBean private MailsClient mailsClient;

    private Long userId;

    @BeforeEach
    void setUp() {
        noticeRepository.deleteAll();
        outgoingEmailRepository.deleteAll();
        auditEventRepository.deleteAll();
        jdbc.update("delete from job_locks");
        jdbc.update("delete from record_access_events");
        jdbc.update("delete from record_shares");
        healthRecordRepository.deleteAll();
        jdbc.update("delete from pending_uploads");
        userRepository.findByFirebaseUid("uid-video-job").ifPresent(userRepository::delete);

        User user = new User();
        user.setFirebaseUid("uid-video-job");
        user.setEmail("video-job@example.com");
        user.setEmailVerified(true);
        user.setRoles(Set.of(Role.MOTHER));
        userId = userRepository.save(user).getId();
        when(mailsClient.send(any(OutgoingMail.class))).thenReturn(true);
    }

    private HealthRecord video(LocalDateTime removalAt, ScanVerdict scan) {
        HealthRecord r = new HealthRecord();
        r.setUserId(userId);
        r.setCategory(RecordCategory.SCAN);
        r.setOriginalFileName("anomaly-scan-week-20.mp4");
        r.setMimeType("video/mp4");
        r.setSizeBytes(1024L);
        r.setStorageBucket("nb-files-test");
        r.setStorageObjectPath("videos/" + UUID.randomUUID());
        r.setStorageProvider(StorageBackend.S3);
        r.setKind(FileKind.VIDEO);
        r.setStatus(RecordStatus.UPLOADED);
        r.setScanStatus(scan);
        r.setRemovalAt(removalAt);
        return healthRecordRepository.save(r);
    }

    private List<String> notices(Long recordId) {
        return noticeRepository.findByRecordIdOrderByIdAsc(recordId).stream().map(VideoRetentionNotice::getKind).toList();
    }

    private long audits(String action) {
        return auditEventRepository.findAll().stream().map(AuditEvent::getAction).filter(action::equals).count();
    }

    private List<OutgoingMail> sentMails() {
        ArgumentCaptor<OutgoingMail> mails = ArgumentCaptor.forClass(OutgoingMail.class);
        verify(mailsClient, atLeast(0)).send(mails.capture());
        return mails.getAllValues();
    }

    @Test
    void day23Warning_sentOnce_linksToTheApp_noFileNameOrS3() {
        LocalDateTime removalAt = LocalDateTime.of(2026, 12, 11, 0, 0);
        HealthRecord v = video(removalAt, ScanVerdict.CLEAN);
        LocalDateTime day23 = removalAt.minusDays(7).plusHours(3);

        assertEquals(1, service.run(day23).warningsSent());
        assertEquals(0, service.run(day23).warningsSent());
        assertEquals(0, service.run(day23.plusHours(20)).warningsSent());

        assertEquals(List.of("WARN_7"), notices(v.getId()));
        List<OutgoingMail> mails = sentMails();
        assertEquals(1, mails.size());
        OutgoingMail mail = mails.get(0);
        assertEquals("VIDEO_REMOVAL_WARNING", mail.eventType());
        assertEquals(userId, mail.userId());
        assertTrue(mail.text().contains("https://narturebelle.com/records?video=" + v.getId()), mail.text());
        assertTrue(mail.text().contains("NartureBelle™"));
        assertFalse(mail.text().contains("amazonaws") || mail.text().contains("anomaly") || mail.subject().contains("anomaly"));
        assertEquals(1, audits("RECORD_VIDEO_WARNING_SENT"));
        assertEquals(1, outgoingEmailRepository.count());
    }

    @Test
    void day29Warning_followsDay23_andALateRunSendsOnlyTheMostUrgent() {
        LocalDateTime removalAt = LocalDateTime.of(2026, 12, 11, 0, 0);
        HealthRecord onTime = video(removalAt, ScanVerdict.CLEAN);
        service.run(removalAt.minusDays(7));
        service.run(removalAt.minusHours(20));
        assertEquals(List.of("WARN_7", "WARN_1"), notices(onTime.getId()));

        // The job was down on day 23: on day 29 she gets one message, not two.
        HealthRecord missed = video(removalAt, ScanVerdict.CLEAN);
        service.run(removalAt.minusHours(20));
        assertEquals(List.of("WARN_1"), notices(missed.getId()));
        assertEquals(3, sentMails().size());
    }

    @Test
    void noWarning_forVideosThatCantBeDownloaded_orDeleted() {
        LocalDateTime removalAt = LocalDateTime.of(2026, 12, 11, 0, 0);
        HealthRecord pending = video(removalAt, ScanVerdict.PENDING);
        HealthRecord blocked = video(removalAt, ScanVerdict.INFECTED);
        HealthRecord deleted = video(removalAt, ScanVerdict.CLEAN);
        deleted.setStatus(RecordStatus.DELETED);
        healthRecordRepository.save(deleted);

        assertEquals(0, service.run(removalAt.minusDays(6)).warningsSent());
        assertTrue(notices(pending.getId()).isEmpty() && notices(blocked.getId()).isEmpty() && notices(deleted.getId()).isEmpty());
        // Unavailable videos still go to trash and are audited; a video she deleted herself is skipped
        // (it was already audited as RECORD_DELETED).
        assertEquals(2, service.run(removalAt.plusDays(1)).trashed());
        assertTrue(notices(deleted.getId()).isEmpty());
    }

    @Test
    void trashedThenPurged_eachAuditedOnce_withoutHealthDetails_andNothingDeleted() {
        LocalDateTime removalAt = LocalDateTime.of(2026, 12, 11, 0, 0);
        HealthRecord v = video(removalAt, ScanVerdict.CLEAN);

        assertEquals(1, service.run(removalAt).trashed());
        assertEquals(0, service.run(removalAt.plusDays(3)).trashed());
        assertEquals(0, service.run(removalAt.plusDays(3)).purged());
        assertEquals(1, service.run(removalAt.plusDays(7)).purged());
        assertEquals(0, service.run(removalAt.plusDays(9)).purged());

        assertEquals(List.of("TRASHED", "PURGED"), notices(v.getId()));
        assertEquals(1, audits("RECORD_VIDEO_TRASHED"));
        assertEquals(1, audits("RECORD_VIDEO_PURGED"));
        assertTrue(auditEventRepository.findAll().stream().allMatch(a -> a.getMetadataJson() == null
                || !a.getMetadataJson().contains("anomaly")));
        assertTrue(sentMails().isEmpty(), "no warning once it's in trash");
        assertEquals(RecordStatus.UPLOADED, healthRecordRepository.findById(v.getId()).orElseThrow().getStatus());
    }

    @Test
    void jobDownForAWhile_catchesUpWithBothAuditEvents() {
        LocalDateTime removalAt = LocalDateTime.of(2026, 12, 11, 0, 0);
        HealthRecord v = video(removalAt, ScanVerdict.CLEAN);
        service.run(removalAt.plusDays(12));
        assertEquals(List.of("TRASHED", "PURGED"), notices(v.getId()));
    }

    @Test
    void restoredVideo_getsANewCycle_warnedAndAuditedAgain() {
        LocalDateTime firstRemoval = LocalDateTime.of(2026, 12, 11, 0, 0);
        HealthRecord v = video(firstRemoval, ScanVerdict.CLEAN);
        service.run(firstRemoval.minusDays(7));
        service.run(firstRemoval.plusDays(2));
        assertEquals(List.of("WARN_7", "TRASHED"), notices(v.getId()));

        // Support restores it on 13 Dec: RecordService.restoreVideo gives it a new 30 days from the copy.
        LocalDateTime restoredAt = firstRemoval.plusDays(2);
        Instant restoredInstant = restoredAt.atZone(ZoneId.systemDefault()).toInstant();
        v.setRemovalAt(rules.removalAt(restoredInstant));
        v.setScanStatus(ScanVerdict.CLEAN);
        healthRecordRepository.save(v);
        LocalDateTime secondRemoval = v.getRemovalAt();
        assertTrue(secondRemoval.isAfter(restoredAt.plusDays(29)));

        assertEquals(0, service.run(restoredAt.plusHours(1)).trashed(), "not trashed again straight after restore");
        assertEquals(1, service.run(secondRemoval.minusDays(7)).warningsSent());
        assertEquals(1, service.run(secondRemoval).trashed());
        assertEquals(List.of("WARN_7", "TRASHED", "WARN_7", "TRASHED"), notices(v.getId()));
        List<OutgoingMail> mails = sentMails();
        assertEquals(2, mails.size());
        assertNotEquals(mails.get(0).eventId(), mails.get(1).eventId());
    }

    @Test
    void timeZoneEdges_matchS3Midnight_andShowTheIndianDate() {
        // Uploaded at 00:15 India time on 11 Nov (18:45 UTC on 10 Nov): S3 counts from UTC.
        LocalDateTime removal = rules.removalAt(Instant.parse("2026-11-10T18:45:00Z"));
        assertEquals(local("2026-12-11T00:00:00Z"), removal);
        assertEquals(local("2026-12-11T00:00:00Z"), rules.removalAt(Instant.parse("2026-11-10T23:59:59Z")));
        assertEquals(local("2026-12-11T00:00:00Z"), rules.removalAt(Instant.parse("2026-11-11T00:00:00Z")),
                "exactly midnight isn't pushed a day later");
        assertEquals(local("2026-12-12T00:00:00Z"), rules.removalAt(Instant.parse("2026-11-11T00:00:01Z")));

        // Midnight UTC is 05:30 the same day in India.
        assertEquals("11 December 2026", VideoRetentionService.indianDate(removal));
        // 23:30 India time on 9 Dec vs 00:30 India time on 10 Dec.
        assertEquals("in 2 days", VideoRetentionService.whenText(removal, local("2026-12-09T18:00:00Z")));
        assertEquals("tomorrow", VideoRetentionService.whenText(removal, local("2026-12-09T19:00:00Z")));

        // The warning is due from exactly 7 days before, not a second earlier.
        assertNull(VideoRetentionService.dueWarning(List.of(7, 1), removal, removal.minusDays(7).minusSeconds(1)));
        assertEquals(7, VideoRetentionService.dueWarning(List.of(7, 1), removal, removal.minusDays(7)));
        assertEquals(1, VideoRetentionService.dueWarning(List.of(7, 1), removal, removal.minusDays(1)));
    }

    @Test
    void lock_secondCopyIsSkipped_untilTheLeaseEnds() {
        LocalDateTime now = LocalDateTime.of(2026, 12, 4, 3, 30);
        video(LocalDateTime.of(2026, 12, 11, 0, 0), ScanVerdict.CLEAN);

        Optional<String> otherCopy = jobLockService.tryAcquire(VideoRetentionService.JOB_NAME, Duration.ofMinutes(30), now);
        assertTrue(otherCopy.isPresent());
        assertTrue(service.runIfFree(now).isEmpty());
        assertTrue(sentMails().isEmpty());

        // A copy that died leaves the lease to run out; then the job runs.
        assertTrue(service.runIfFree(now.plusMinutes(31)).isPresent());
        assertEquals(1, sentMails().size());
        // Released after the run, so the next run isn't blocked.
        assertTrue(service.runIfFree(now.plusMinutes(32)).isPresent());
    }

    @Test
    void twoCopiesRunningTogether_doEachThingOnce() throws Exception {
        LocalDateTime removalAt = LocalDateTime.of(2026, 12, 11, 0, 0);
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 5; i++) ids.add(video(removalAt, ScanVerdict.CLEAN).getId());
        HealthRecord trashed = video(LocalDateTime.of(2026, 12, 1, 0, 0), ScanVerdict.CLEAN);
        LocalDateTime now = removalAt.minusDays(6);

        // Both with the lock (the normal path) and without it (as if the lock failed): never duplicated.
        for (boolean useLock : new boolean[]{true, false}) {
            CountDownLatch start = new CountDownLatch(1);
            ExecutorService pool = Executors.newFixedThreadPool(2);
            Callable<Object> copy = () -> {
                start.await();
                return useLock ? service.runIfFree(now) : service.run(now);
            };
            Future<Object> a = pool.submit(copy);
            Future<Object> b = pool.submit(copy);
            start.countDown();
            a.get();
            b.get();
            pool.shutdown();
        }

        for (Long id : ids) assertEquals(List.of("WARN_7"), notices(id));
        assertEquals(List.of("TRASHED"), notices(trashed.getId()));
        assertEquals(5, outgoingEmailRepository.count());
        assertEquals(5, sentMails().size());
        assertEquals(1, audits("RECORD_VIDEO_TRASHED"));
    }

    private static LocalDateTime local(String utcInstant) {
        return LocalDateTime.ofInstant(Instant.parse(utcInstant), ZoneId.systemDefault());
    }
}
