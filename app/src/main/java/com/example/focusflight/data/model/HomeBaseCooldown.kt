package com.example.focusflight.data.model

/**
 * Pure cooldown math for docs/modes.md's two home-base actions - no
 * Room/SharedPreferences/Context dependency, mirroring [ChallengeProgress]/[AchievementProgress]'s
 * pattern so this is directly unit-testable (see HomeBaseCooldownTest) independent of
 * `PreferencesRepository`.
 *
 * Two distinct cooldowns gate two distinct actions (story-mode.md's "Cooldowns" - don't conflate
 * them): the 7-day return-home teleport, and the 30-day change-home-base. Both use the same
 * rolling-elapsed-time check below (`now - lastTimestamp >= days * 24h` in millis) rather than
 * calendar-day boundaries - nothing in the design implies calendar-day semantics, and a rolling
 * window is the simpler, unambiguous choice.
 */
object HomeBaseCooldown {
    const val RETURN_HOME_COOLDOWN_DAYS = 7
    const val CHANGE_HOME_BASE_COOLDOWN_DAYS = 30

    private const val DAY_MS = 24L * 60L * 60L * 1000L

    /**
     * True if an action last performed at [lastTimestamp] (millis epoch, null if never performed)
     * is eligible to run again at [now], given a [cooldownDays]-day rolling cooldown. A null
     * [lastTimestamp] is always eligible - this is also how a *seeded* timestamp
     * ([seedChangeHomeBaseTimestamp]) becomes eligible without a separate code path: it's just a
     * non-null timestamp far enough in the past to already satisfy the same check.
     */
    fun isEligible(now: Long, lastTimestamp: Long?, cooldownDays: Int): Boolean {
        if (lastTimestamp == null) return true
        return now - lastTimestamp >= cooldownDays * DAY_MS
    }

    /**
     * Milliseconds remaining until [isEligible] would return true - 0 if already eligible (never
     * negative), for driving a "come back in N days" display without duplicating the eligibility
     * check's arithmetic at each call site.
     */
    fun remainingMillis(now: Long, lastTimestamp: Long?, cooldownDays: Int): Long {
        if (lastTimestamp == null) return 0L
        val elapsed = now - lastTimestamp
        val cooldownMs = cooldownDays * DAY_MS
        return (cooldownMs - elapsed).coerceAtLeast(0L)
    }

    /**
     * The "last home base changed" timestamp to seed at onboarding, per story-mode.md's
     * "Changing home base needs no separate 'grace change' mechanic": 31 days before [now], one
     * day past [CHANGE_HOME_BASE_COOLDOWN_DAYS], so the very first real change-home-base call
     * [isEligible] the exact same way every later change does, instead of a special-cased
     * "first time" branch.
     */
    fun seedChangeHomeBaseTimestamp(now: Long): Long = now - (CHANGE_HOME_BASE_COOLDOWN_DAYS + 1) * DAY_MS
}
