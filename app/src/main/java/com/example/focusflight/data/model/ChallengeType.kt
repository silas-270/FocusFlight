package com.example.focusflight.data.model

/**
 * The three challenge types from docs/design/challenges.md ("Shape at a glance"). This is the
 * complete v1 list - no other types planned.
 */
enum class ChallengeType {
    /** Departure-to-destination, own isolated position pointer, progress via straight-line
     *  proxy. See [ChallengeProgress.routeProgress]. */
    ROUTE,

    /** Count of set members reached / total set size, tracked fresh per instance (never seeded
     *  from Story Mode's visited-set - see challenges.md#isolation). Curated only. */
    SET_COMPLETION,

    /** Cumulative real distance flown while active / a target distance. No position pointer. */
    DISTANCE
}
