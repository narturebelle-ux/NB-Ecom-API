package com.narturebelle.service;

import com.narturebelle.exception.InvalidRecordRequestException;
import com.narturebelle.model.FileKind;
import com.narturebelle.model.HealthRecord;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

class RecordFileRulesTest {

    private final RecordFileRules rules = new RecordFileRules(
            "application/pdf,image/jpeg,image/png,image/heic", 26214400L,
            "application/msword,application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            10737418240L, "video/mp4,video/quicktime", 2147483648L, 67108864L, 16777216L, 30, 7, "7,1");

    @Test
    void kinds_andWordIsBlockedEvenIfAllowedElsewhere() {
        assertEquals(FileKind.DOCUMENT, rules.kindFor("application/pdf"));
        assertEquals(FileKind.VIDEO, rules.kindFor("video/mp4"));
        assertThrows(InvalidRecordRequestException.class, () -> rules.kindFor("application/msword"));
        assertThrows(InvalidRecordRequestException.class,
                () -> rules.kindFor("application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
        assertThrows(InvalidRecordRequestException.class, () -> rules.kindFor("application/x-msdownload"));
        assertEquals(26214400L, rules.maxBytes(FileKind.DOCUMENT));
    }

    @Test
    void removalAt_matchesS3Lifecycle_30DaysThenNextMidnightUtc() {
        Instant created = Instant.parse("2026-11-10T09:15:00Z");
        LocalDateTime expected = LocalDateTime.ofInstant(Instant.parse("2026-12-11T00:00:00Z"), ZoneId.systemDefault());
        assertEquals(expected, rules.removalAt(created));

        HealthRecord video = new HealthRecord();
        video.setRemovalAt(expected);
        assertEquals(expected.minusDays(7), rules.warnFrom(video)); // day 23
        assertEquals(expected.plusDays(7), rules.permanentDeletionAt(video));
        assertNull(rules.trashedAt(video, expected.minusSeconds(1)));
        assertEquals(expected, rules.trashedAt(video, expected));
        assertFalse(rules.isGone(video, expected.plusDays(6)));
        assertTrue(rules.isGone(video, expected.plusDays(7)));
    }

    @Test
    void multipart_onlyForLargeVideos_andPartsFitS3Limits() {
        assertFalse(rules.needsMultipart(FileKind.DOCUMENT, 100_000_000L));
        assertFalse(rules.needsMultipart(FileKind.VIDEO, 10_000_000L));
        assertTrue(rules.needsMultipart(FileKind.VIDEO, 100_000_000L));
        assertEquals(16777216L, rules.partSizeFor(2147483648L));
        assertTrue(rules.partSizeFor(500L * 1024 * 1024 * 1024) * 10_000 >= 500L * 1024 * 1024 * 1024);
    }
}
