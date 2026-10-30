package com.narturebelle.service;

import com.narturebelle.dto.SessionWindowsResponse;
import com.narturebelle.dto.SessionWindowsResponse.Window;
import com.narturebelle.model.*;
import com.narturebelle.repository.CareProtocolStepRepository;
import com.narturebelle.repository.JourneyRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Suggested date ranges for each professional's sessions, from her due date and the care protocol
 * (which professional belongs to which stage). Pregnancy is counted as 40 weeks ending on the due date.
 * Protocol rows are PENDING_CLINICAL_REVIEW, so this is general guidance (guidanceOnly) and never blocks
 * a booking by itself -- a booking outside a window only gets a timing note.
 */
@Service
public class SessionWindowService {

    private static final int PREGNANCY_DAYS = 280;
    private static final DateTimeFormatter NOTE_DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    private final JourneyRepository journeyRepository;
    private final CareProtocolStepRepository careProtocolStepRepository;

    public SessionWindowService(JourneyRepository journeyRepository, CareProtocolStepRepository careProtocolStepRepository) {
        this.journeyRepository = journeyRepository;
        this.careProtocolStepRepository = careProtocolStepRepository;
    }

    public SessionWindowsResponse windowsFor(Long userId) {
        Journey journey = activeJourney(userId);
        if (journey == null || journey.getExpectedDeliveryDate() == null) {
            return new SessionWindowsResponse(null, null, true, List.of());
        }
        LocalDate today = LocalDate.now();
        LocalDate due = journey.getExpectedDeliveryDate();
        boolean guidanceOnly = careProtocolStepRepository.findAll().stream()
                .anyMatch(step -> step.getReviewStatus() != ReviewStatus.APPROVED);
        List<Window> windows = allWindows(due).stream()
                .filter(w -> !w.to().isBefore(today))
                .map(w -> new Window(w.professionalType(), w.title(), w.description(),
                        w.from().isBefore(today) ? today : w.from(), w.to(), !w.from().isAfter(today), w.optional()))
                .toList();
        return new SessionWindowsResponse(due, Math.max(0, ChronoUnit.WEEKS.between(today, due)), guidanceOnly, windows);
    }

    /** A friendly note when a session falls outside every suggested range for that professional, else null. */
    public String timingNote(Long userId, Role professionalType, LocalDate sessionDate) {
        Journey journey = activeJourney(userId);
        if (journey == null || journey.getExpectedDeliveryDate() == null) return null;
        List<Window> forType = allWindows(journey.getExpectedDeliveryDate()).stream()
                .filter(w -> w.professionalType() == professionalType)
                .toList();
        if (forType.isEmpty()) return null;
        boolean inside = forType.stream().anyMatch(w -> !sessionDate.isBefore(w.from()) && !sessionDate.isAfter(w.to()));
        if (inside) return null;
        Window closest = forType.stream()
                .min(Comparator.comparingLong(w -> Math.min(
                        Math.abs(ChronoUnit.DAYS.between(sessionDate, w.from())),
                        Math.abs(ChronoUnit.DAYS.between(sessionDate, w.to())))))
                .orElseThrow();
        return "This is outside the usual time for this session. It's often most useful between "
                + NOTE_DATE.format(closest.from()) + " and " + NOTE_DATE.format(closest.to()) + ".";
    }

    private Journey activeJourney(Long userId) {
        return journeyRepository.findByUserIdAndStatusNot(userId, JourneyStatus.CLOSED).orElse(null);
    }

    /** Every protocol step placed on the calendar, earliest first (past ones included). */
    private List<Window> allWindows(LocalDate due) {
        List<Window> windows = new ArrayList<>();
        for (CareProtocolStep step : careProtocolStepRepository.findAll()) {
            LocalDate[] range = rangeFor(step, due);
            if (range != null) {
                windows.add(new Window(step.getProfessionalType(), step.getTitle(), step.getDescription(),
                        range[0], range[1], false, step.isOptional()));
            }
        }
        windows.sort(Comparator.comparing(Window::from));
        return windows;
    }

    /** Trimesters: weeks 1-13, 14-27, 28-36. Pre-delivery: week 37 to the due date. After birth: 6 weeks, then to 6 months. */
    private static LocalDate[] rangeFor(CareProtocolStep step, LocalDate due) {
        JourneyStatus stage = step.getJourneyStatus();
        if (stage == null) return null;
        LocalDate start = due.minusDays(PREGNANCY_DAYS);
        return switch (stage) {
            case PREGNANCY -> {
                if (step.getTrimester() == null) yield null;
                yield switch (step.getTrimester()) {
                    case 1 -> new LocalDate[]{start, start.plusWeeks(13).minusDays(1)};
                    case 2 -> new LocalDate[]{start.plusWeeks(13), start.plusWeeks(27).minusDays(1)};
                    case 3 -> new LocalDate[]{start.plusWeeks(27), start.plusWeeks(36).minusDays(1)};
                    default -> null;
                };
            }
            case PRE_DELIVERY -> new LocalDate[]{start.plusWeeks(36), due};
            case DELIVERY_COMPLETED -> new LocalDate[]{due, due.plusWeeks(6)};
            case POSTPARTUM -> new LocalDate[]{due.plusWeeks(6), due.plusWeeks(26)};
            default -> null;
        };
    }
}
