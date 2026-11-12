package com.narturebelle.storage;

import com.narturebelle.model.HealthRecord;
import com.narturebelle.model.ScanVerdict;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/** RECORDS_SCAN_REQUIRED (owner, 11 Nov 2026): may be off in stage and production until GuardDuty is set up; never silently. */
@org.junit.jupiter.api.extension.ExtendWith(OutputCaptureExtension.class)
class ScanPolicyTest {

    private final ApplicationContextRunner context = new ApplicationContextRunner().withBean(ScanPolicy.class);

    /** Owner decision, 11 Nov 2026: production may run without scanning until GuardDuty, but never silently. */
    @Test
    void prodProfile_withScanningOff_startsButLogsAnError(CapturedOutput output) {
        context.withPropertyValues("records.scan.required=false")
                .withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles("prod"))
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertFalse(ctx.getBean(ScanPolicy.class).required());
                });
        assertThat(output.getAll()).contains("ERROR").contains("PRODUCTION IS RUNNING WITHOUT MALWARE SCANNING");
    }

    @Test
    void prodProfile_default_startsWithScanningRequired() {
        context.withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles("prod"))
                .run(ctx -> assertTrue(ctx.getBean(ScanPolicy.class).required()));
    }

    @Test
    void stageProfile_mayTurnScanningOff() {
        context.withPropertyValues("records.scan.required=false")
                .withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles("stage"))
                .run(ctx -> assertFalse(ctx.getBean(ScanPolicy.class).required()));
    }

    @Test
    void downloads_requireClean_unlessScanningIsOff() {
        ScanPolicy on = new ScanPolicy(true, new MockEnvironment());
        ScanPolicy off = new ScanPolicy(false, new MockEnvironment());

        assertEquals(ScanVerdict.PENDING, on.initialS3Verdict());
        assertEquals(ScanVerdict.NOT_SCANNED, off.initialS3Verdict());

        assertTrue(on.allowsDownload(s3(ScanVerdict.CLEAN)));
        assertFalse(on.allowsDownload(s3(ScanVerdict.PENDING)));
        assertFalse(on.allowsDownload(s3(ScanVerdict.NOT_SCANNED)), "stage test files are refused once scanning is on");
        assertTrue(off.allowsDownload(s3(ScanVerdict.NOT_SCANNED)));
        assertFalse(off.allowsDownload(s3(ScanVerdict.INFECTED)));
        assertFalse(off.allowsDownload(s3(ScanVerdict.SCAN_FAILED)));

        HealthRecord supabase = new HealthRecord();
        supabase.setStorageProvider(StorageBackend.SUPABASE);
        supabase.setScanStatus(ScanVerdict.NOT_SCANNED);
        assertTrue(on.allowsDownload(supabase), "old Supabase files keep today's behaviour (F9-S6)");
    }

    private static HealthRecord s3(ScanVerdict verdict) {
        HealthRecord r = new HealthRecord();
        r.setStorageProvider(StorageBackend.S3);
        r.setScanStatus(verdict);
        return r;
    }
}
