package com.narturebelle.scheduling;

import com.narturebelle.dto.DashboardResponse;
import com.narturebelle.mail.DueReminder;
import com.narturebelle.mail.MailsClient;
import com.narturebelle.model.Booking;
import com.narturebelle.model.BookingStatus;
import com.narturebelle.model.Journey;
import com.narturebelle.model.User;
import com.narturebelle.repository.BookingRepository;
import com.narturebelle.repository.JourneyRepository;
import com.narturebelle.repository.UserRepository;
import com.narturebelle.model.JourneyStatus;
import com.narturebelle.service.ConsentService;
import com.narturebelle.service.DashboardService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * Decides which reminders are due from bookings and the journey timeline, per the roadmap's Phase 5 bullet, and tells
 * NB-Ecom-Notifications-API, which owns the reminder rows, their wording and delivery. "Consent-checked": a user with pending
 * required consents is skipped entirely, same gate Journey/Dashboard already enforce. Only IDs leave this API, never
 * health details. Repeats are safe, so a failed run is simply covered by the next one.
 */
@Component
@ConditionalOnProperty(prefix = "narturebelle.scheduling", name = "enabled", matchIfMissing = true)
public class ReminderGenerationJob {

    private static final Logger log = LoggerFactory.getLogger(ReminderGenerationJob.class);
    private static final int BATCH_SIZE = 500;

    private final BookingRepository bookingRepository;
    private final JourneyRepository journeyRepository;
    private final UserRepository userRepository;
    private final DashboardService dashboardService;
    private final ConsentService consentService;
    private final MailsClient mailsClient;
    private final byte[] dedupeSecret;

    public ReminderGenerationJob(BookingRepository bookingRepository, JourneyRepository journeyRepository,
                                  UserRepository userRepository, DashboardService dashboardService,
                                  ConsentService consentService, MailsClient mailsClient,
                                  @Value("${reminders.dedupe-secret}") String dedupeSecret) {
        this.bookingRepository = bookingRepository;
        this.journeyRepository = journeyRepository;
        this.userRepository = userRepository;
        this.dashboardService = dashboardService;
        this.consentService = consentService;
        this.mailsClient = mailsClient;
        this.dedupeSecret = dedupeSecret.getBytes(StandardCharsets.UTF_8);
    }

    @Scheduled(fixedRate = 900_000) // every 15 minutes
    public void generateBookingReminders() {
        LocalDateTime now = LocalDateTime.now();
        List<Booking> upcoming = bookingRepository.findByStatusAndScheduledStartBetween(
                BookingStatus.BOOKED, now, now.plusHours(24));
        List<DueReminder> due = new ArrayList<>();
        for (Booking booking : upcoming) {
            if (!consentService.pendingConsentTypes(booking.getUserId()).isEmpty()) {
                continue;
            }
            due.add(new DueReminder(booking.getUserId(), "BOOKING_UPCOMING", "BOOKING", booking.getId(), booking.getScheduledStart(), null));
        }
        send(due);
    }

    @Scheduled(fixedRate = 900_000) // every 15 minutes
    public void generateJourneyReminders() {
        List<Journey> activeJourneys = journeyRepository.findByStatusNot(JourneyStatus.CLOSED);
        List<DueReminder> due = new ArrayList<>();
        for (Journey journey : activeJourneys) {
            if (!consentService.pendingConsentTypes(journey.getUserId()).isEmpty()) {
                continue;
            }
            User user = userRepository.findById(journey.getUserId()).orElse(null);
            if (user == null) {
                continue;
            }
            DashboardResponse dashboard = dashboardService.build(user);
            if (dashboard.getNextActions() != null && !dashboard.getNextActions().isEmpty()) {
                due.add(new DueReminder(journey.getUserId(), "JOURNEY_NEXT_ACTION", "JOURNEY", journey.getId(), null, stageKey(journey)));
            }
        }
        send(due);
    }

    /** One next-step reminder per journey stage, so a dismissal holds until the stage changes. Keyed with a secret only
     * this API holds: a journey stage is health information, and a plain hash of so few values could be worked out. */
    private String stageKey(Journey journey) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(dedupeSecret, "HmacSHA256"));
            byte[] hash = mac.doFinal((journey.getId() + ":" + journey.getStatus()).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 16);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private void send(List<DueReminder> due) {
        for (int from = 0; from < due.size(); from += BATCH_SIZE) {
            try {
                mailsClient.sendReminders(due.subList(from, Math.min(from + BATCH_SIZE, due.size())));
            } catch (Exception e) {
                log.warn("Could not send due reminders; the next run repeats them: {}", e.getClass().getSimpleName());
                return;
            }
        }
    }
}
