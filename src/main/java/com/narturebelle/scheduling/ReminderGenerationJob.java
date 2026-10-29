package com.narturebelle.scheduling;

import com.narturebelle.dto.DashboardResponse;
import com.narturebelle.model.Booking;
import com.narturebelle.model.BookingStatus;
import com.narturebelle.model.Journey;
import com.narturebelle.model.ReminderSourceType;
import com.narturebelle.model.ReminderType;
import com.narturebelle.model.User;
import com.narturebelle.repository.BookingRepository;
import com.narturebelle.repository.JourneyRepository;
import com.narturebelle.repository.UserRepository;
import com.narturebelle.model.JourneyStatus;
import com.narturebelle.service.ConsentService;
import com.narturebelle.service.DashboardService;
import com.narturebelle.service.NotificationService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Generates reminders from bookings and the journey timeline, per the roadmap's Phase 5 bullet.
 * "Consent-checked": a user with pending required consents is skipped entirely, same gate Journey/
 * Dashboard already enforce. Message text is deliberately generic -- no protocol-step titles, no
 * provider names, no clinical specifics -- per "no health details in message text."
 */
@Component
@ConditionalOnProperty(prefix = "narturebelle.scheduling", name = "enabled", matchIfMissing = true)
public class ReminderGenerationJob {

    private final BookingRepository bookingRepository;
    private final JourneyRepository journeyRepository;
    private final UserRepository userRepository;
    private final DashboardService dashboardService;
    private final ConsentService consentService;
    private final NotificationService notificationService;

    public ReminderGenerationJob(BookingRepository bookingRepository, JourneyRepository journeyRepository,
                                  UserRepository userRepository, DashboardService dashboardService,
                                  ConsentService consentService, NotificationService notificationService) {
        this.bookingRepository = bookingRepository;
        this.journeyRepository = journeyRepository;
        this.userRepository = userRepository;
        this.dashboardService = dashboardService;
        this.consentService = consentService;
        this.notificationService = notificationService;
    }

    @Scheduled(fixedRate = 900_000) // every 15 minutes
    public void generateBookingReminders() {
        LocalDateTime now = LocalDateTime.now();
        List<Booking> upcoming = bookingRepository.findByStatusAndScheduledStartBetween(
                BookingStatus.BOOKED, now, now.plusHours(24));
        for (Booking booking : upcoming) {
            if (!consentService.pendingConsentTypes(booking.getUserId()).isEmpty()) {
                continue;
            }
            notificationService.notify(booking.getUserId(), ReminderType.BOOKING_UPCOMING,
                    "Upcoming care session",
                    "You have an upcoming care session scheduled in the next 24 hours.",
                    ReminderSourceType.BOOKING, booking.getId(), booking.getScheduledStart());
        }
    }

    @Scheduled(fixedRate = 900_000) // every 15 minutes
    public void generateJourneyReminders() {
        List<Journey> activeJourneys = journeyRepository.findByStatusNot(JourneyStatus.CLOSED);
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
                notificationService.notify(journey.getUserId(), ReminderType.JOURNEY_NEXT_ACTION,
                        "New recommended next steps",
                        "Your care journey has recommended next steps waiting -- check My Journey for details.",
                        ReminderSourceType.JOURNEY, journey.getId(), null);
            }
        }
    }
}
