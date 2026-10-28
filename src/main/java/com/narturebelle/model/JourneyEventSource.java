package com.narturebelle.model;

/** Who caused a JourneyStageEvent: the mother herself, clinical/support staff, or a system process (e.g. migration backfill). */
public enum JourneyEventSource {
    USER,
    STAFF,
    SYSTEM
}
