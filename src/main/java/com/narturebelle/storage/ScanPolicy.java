package com.narturebelle.storage;

import com.narturebelle.model.HealthRecord;
import com.narturebelle.model.ScanVerdict;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * Whether S3 uploads must pass the GuardDuty malware scan before they can be opened (F9-S19 item 7).
 * RECORDS_SCAN_REQUIRED=false turns scanning off: new S3 uploads are then NOT_SCANNED and open at once.
 *
 * Owner decision, 11 Nov 2026: GuardDuty isn't set up yet, so scanning may be off in stage AND production
 * while S3 is tested; the owner turns it on once GuardDuty Malware Protection is enabled. This replaces the
 * earlier "production refuses to start" rule. Never silent: production with scanning off logs an ERROR at
 * every startup, and each unscanned upload is logged and audited (malwareScanning: OFF). F9-S6 and F9-S17
 * stay open until it is back on.
 *
 * An S3 file saved as NOT_SCANNED opens only while scanning is off. Once it's switched on, those files are
 * treated as not ready, so nothing unscanned slips through.
 */
@Component
public class ScanPolicy {

    private static final Logger log = LoggerFactory.getLogger(ScanPolicy.class);

    private final boolean required;

    public ScanPolicy(@Value("${records.scan.required:true}") boolean required, Environment environment) {
        if (!required && environment.acceptsProfiles(Profiles.of("prod"))) {
            log.error("PRODUCTION IS RUNNING WITHOUT MALWARE SCANNING (RECORDS_SCAN_REQUIRED=false): mothers' "
                    + "uploads open without a scan. Owner decision 11 Nov 2026, until GuardDuty is set up. "
                    + "Turn GuardDuty on and set RECORDS_SCAN_REQUIRED=true before real users (F9-S6, F9-S17).");
        } else if (!required) {
            log.warn("Malware scanning is OFF: new S3 uploads open without a scan (RECORDS_SCAN_REQUIRED=false).");
        }
        this.required = required;
    }

    public boolean required() {
        return required;
    }

    /** What a new S3 upload starts as. */
    public ScanVerdict initialS3Verdict() {
        return required ? ScanVerdict.PENDING : ScanVerdict.NOT_SCANNED;
    }

    /** Supabase files keep today's behaviour (F9-S6). S3 files need CLEAN, or NOT_SCANNED while scanning is off. */
    public boolean allowsDownload(HealthRecord record) {
        return allowsDownload(record.getStorageProvider(), record.getScanStatus());
    }

    /** The same rule for any stored file (provider documents, résumés). */
    public boolean allowsDownload(StorageBackend backend, ScanVerdict verdict) {
        if (backend != StorageBackend.S3) {
            return true;
        }
        return verdict == ScanVerdict.CLEAN || (verdict == ScanVerdict.NOT_SCANNED && !required);
    }
}
