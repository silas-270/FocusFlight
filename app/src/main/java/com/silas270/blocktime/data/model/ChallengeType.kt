package com.silas270.blocktime.data.model

/**
 * The challenge types. The first three are the original v1 set; [STREAK] was added afterwards to
 * carry the short-horizon "don't stop now" case that Tours deliberately do not.
 */
enum class ChallengeType {
    /** Departure-to-destination, own isolated position pointer, progress via straight-line
     *  proxy. See [ChallengeProgress.routeProgress]. */
    ROUTE,

    /** Count of set members reached / total set size, tracked fresh per instance (never seeded
     *  from Story Mode's visited-set - see challenges.md#isolation). Curated only. */
    SET_COMPLETION,

    /** Cumulative real distance flown while active / a target distance. No position pointer. */
    DISTANCE,

    /**
     * Consecutive local calendar days with at least one flight, against a small target - a final
     * push in the last few days before an exam, where breaking on a miss is the point.
     *
     * That fragility is deliberate *because* [com.silas270.blocktime.data.model.Tour] exists to
     * cover the opposite horizon: tours are weeks long and never break, so this one is free to be
     * short and unforgiving. A long unforgiving streak would be the worst of both, which is why
     * targets here are 3-5 days rather than 30.
     */
    STREAK
}
