package com.example.focusflight.data.model

/**
 * Tags every flight/logbook session with exactly one mode, per
 * docs/modes.md ("Mode tags"). Stored directly on [FlightLog] so the
 * post-landing pipeline (see `InFlightViewModel.completeFlight()`) can branch on
 * it instead of computing it after the fact. Write-once at session start —
 * nothing in the design implies reclassifying a flight later.
 *
 * See mechanics.md's isolation matrix for the full read/write rules per tag.
 * This phase only introduces the tag itself; FREE and CHALLENGE aren't produced
 * anywhere yet (Free Mode / Challenges are later phases) — every flight logged
 * today is STORY.
 */
enum class FlightMode {
    /** Normal Story Mode flying (the default, unchanged today). Full read/write
     *  of `currentAirport` and the visited-set. */
    STORY,

    /** A Free Mode session. Logged like any other flight, but never writes
     *  `currentAirport`/visited-set and never counts toward achievements or
     *  challenges. Not yet produced anywhere - see Phase 2 (Free Mode). */
    FREE,

    /** A session flown explicitly under one active Route challenge. Doesn't
     *  write `currentAirport`, but does move that challenge's own position
     *  pointer. Not yet produced anywhere - see Phase 3 (Challenges). */
    CHALLENGE
}
