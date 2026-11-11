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
 * RECORDS_SCAN_REQUIRED=false is for pre-production testing without GuardDuty only (owner, 11 Nov 2026):
 * new S3 uploads are then NOT_SCANNED and open at once, like old Supabase files. Production refuses to
 * start with it off (F9-S6, F9-S17): there is no silent fallback.
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
            throw new IllegalStateException("RECORDS_SCAN_REQUIRED=false is not allowed in production: "
                    + "every upload must be scanned before it can be opened (F9-S6, F9-S17).");
        }
        if (!required) {
            log.warn("Malware scanning is OFF: new S3 uploads open without a scan (RECORDS_SCAN_REQUIRED=false). "
                    + "Pre-production testing only.");
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
        if (record.getStorageProvider() != StorageBackend.S3) {
            return true;
        }
        ScanVerdict verdict = record.getScanStatus();
        return verdict == ScanVerdict.CLEAN || (verdict == ScanVerdict.NOT_SCANNED && !required);
    }
}
