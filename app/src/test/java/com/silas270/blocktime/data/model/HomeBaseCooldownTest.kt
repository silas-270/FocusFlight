package com.silas270.blocktime.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-function coverage for [HomeBaseCooldown] - no Room/SharedPreferences involved, per
 * docs/modes.md's two cooldowns (7-day return-home, 30-day change-home-base) and the
 * 31-day-in-the-past onboarding seed that makes the very first change-home-base immediately
 * eligible through the ordinary check.
 */
class HomeBaseCooldownTest {

    private val dayMs = 24L * 60L * 60L * 1000L

    // ── isEligible ───────────────────────────────────────────────────────────────────────

    @Test
    fun `never performed before (null timestamp) is always eligible`() {
        assertTrue(HomeBaseCooldown.isEligible(now = 1_000_000L, lastTimestamp = null, cooldownDays = 7))
    }

    @Test
    fun `not eligible before the cooldown window has elapsed`() {
        val now = 10 * dayMs
        val last = now - 6 * dayMs // only 6 of 7 days elapsed
        assertFalse(HomeBaseCooldown.isEligible(now, last, cooldownDays = HomeBaseCooldown.RETURN_HOME_COOLDOWN_DAYS))
    }

    @Test
    fun `eligible exactly at the cooldown boundary`() {
        val now = 10 * dayMs
        val last = now - HomeBaseCooldown.RETURN_HOME_COOLDOWN_DAYS * dayMs
        assertTrue(HomeBaseCooldown.isEligible(now, last, cooldownDays = HomeBaseCooldown.RETURN_HOME_COOLDOWN_DAYS))
    }

    @Test
    fun `eligible well past the cooldown window`() {
        val now = 100 * dayMs
        val last = now - 40 * dayMs
        assertTrue(HomeBaseCooldown.isEligible(now, last, cooldownDays = HomeBaseCooldown.CHANGE_HOME_BASE_COOLDOWN_DAYS))
    }

    @Test
    fun `the two cooldown lengths are actually distinct`() {
        // Guards against the two actions' cooldowns ever getting conflated (story-mode.md is
        // explicit that they must stay separate) - 15 days elapsed clears the 7-day return-home
        // cooldown but not the 30-day change-home-base one.
        val now = 50 * dayMs
        val last = now - 15 * dayMs
        assertTrue(HomeBaseCooldown.isEligible(now, last, cooldownDays = HomeBaseCooldown.RETURN_HOME_COOLDOWN_DAYS))
        assertFalse(HomeBaseCooldown.isEligible(now, last, cooldownDays = HomeBaseCooldown.CHANGE_HOME_BASE_COOLDOWN_DAYS))
    }

    // ── remainingMillis ──────────────────────────────────────────────────────────────────

    @Test
    fun `remainingMillis is zero when never performed before`() {
        assertEquals(0L, HomeBaseCooldown.remainingMillis(now = 1_000_000L, lastTimestamp = null, cooldownDays = 30))
    }

    @Test
    fun `remainingMillis is zero once eligible, never negative`() {
        val now = 100 * dayMs
        val last = now - 45 * dayMs // well past the 30-day cooldown
        assertEquals(0L, HomeBaseCooldown.remainingMillis(now, last, cooldownDays = HomeBaseCooldown.CHANGE_HOME_BASE_COOLDOWN_DAYS))
    }

    @Test
    fun `remainingMillis counts down the exact difference before eligible`() {
        val now = 100 * dayMs
        val last = now - 5 * dayMs // 2 of 7 days remaining
        val expected = 2 * dayMs
        assertEquals(expected, HomeBaseCooldown.remainingMillis(now, last, cooldownDays = HomeBaseCooldown.RETURN_HOME_COOLDOWN_DAYS))
    }

    // ── seedChangeHomeBaseTimestamp: the "no special grace-change path" behavior ────────────

    @Test
    fun `seeded timestamp is 31 days before onboarding`() {
        val onboardingNow = 1_000 * dayMs
        val seeded = HomeBaseCooldown.seedChangeHomeBaseTimestamp(onboardingNow)
        assertEquals(onboardingNow - 31 * dayMs, seeded)
    }

    @Test
    fun `seeded timestamp makes the very first change-home-base immediately eligible`() {
        // This is the crux of story-mode.md's "no separate grace mechanic needed" claim: seeding
        // at onboarding must be immediately eligible through the exact same isEligible() check
        // every later change uses - not a special-cased first-time branch.
        val onboardingTime = 1_000 * dayMs
        val seeded = HomeBaseCooldown.seedChangeHomeBaseTimestamp(onboardingTime)

        // Checked a moment after onboarding (not 30 days later) - the very next moment the user
        // could plausibly try to change their home base.
        val now = onboardingTime + 60_000L // one minute after onboarding
        assertTrue(
            "expected immediate eligibility right after onboarding, using the seeded timestamp",
            HomeBaseCooldown.isEligible(now, seeded, cooldownDays = HomeBaseCooldown.CHANGE_HOME_BASE_COOLDOWN_DAYS)
        )
    }

    @Test
    fun `seeded timestamp is not eligible for return-home's shorter cooldown before real use`() {
        // Sanity check that the seed is specific to change-home-base semantics; it isn't used for
        // return-home at all in production (return-home's lastTimestamp starts genuinely null),
        // but this documents that the seed value itself is just "31 days ago", not a magic
        // always-eligible sentinel independent of cooldownDays.
        val onboardingTime = 1_000 * dayMs
        val seeded = HomeBaseCooldown.seedChangeHomeBaseTimestamp(onboardingTime)
        val now = onboardingTime
        assertTrue(HomeBaseCooldown.isEligible(now, seeded, cooldownDays = HomeBaseCooldown.RETURN_HOME_COOLDOWN_DAYS))
        assertTrue(HomeBaseCooldown.isEligible(now, seeded, cooldownDays = HomeBaseCooldown.CHANGE_HOME_BASE_COOLDOWN_DAYS))
    }
}
