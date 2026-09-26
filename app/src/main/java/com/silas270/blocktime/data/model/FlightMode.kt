package com.silas270.blocktime.data.model

/**
 * Tags every flight/logbook session with exactly one mode, per
 * docs/modes.md ("Mode tags"). Stored directly on [FlightLog] so the
 * post-landing pipeline (see `InFlightViewModel.completeFlight()`) can branch on
 * it instead of computing it after the fact. Write-once at session start —
 * nothing in the design implies reclassifying a flight later.
 *
 * See docs/modes.md's isolation matrix for the full read/write rules per tag.
 */
enum class FlightMode {
    /** Normal Story Mode flying, and the default. Full read/write
     *  of `currentAirport` and the visited-set. */
    STORY,

    /** A Free Mode session. Logged like any other flight, but never writes
     *  `currentAirport`/visited-set and never counts toward achievements or
     *  challenges. */
    FREE,

    /** A session flown explicitly under one active Route challenge. Doesn't
     *  write `currentAirport`, but does move that challenge's own position
     *  pointer. */
    CHALLENGE
}
