package com.narturebelle.dto;

import com.narturebelle.model.Role;

import java.time.LocalDate;
import java.util.List;

/** Suggested session dates. dueDate and weeksRemaining are null when she has no journey with a due date
 * (windows is then empty). guidanceOnly: the timings aren't clinically approved yet -- label them so. */
public record SessionWindowsResponse(LocalDate dueDate, Long weeksRemaining, boolean guidanceOnly, List<Window> windows) {

    /** current: today falls inside this range. Finished ranges are left out, and from is never before today. */
    public record Window(Role professionalType, String title, String description, LocalDate from, LocalDate to,
                         boolean current, boolean optional) {}
}
