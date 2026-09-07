package com.example.focusflight.ui.screens.account

import com.example.focusflight.data.model.AchievementBoard
import com.example.focusflight.data.model.AchievementCategory
import com.example.focusflight.data.model.AchievementStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Coverage for [buildAchievementStacks] - the fold that turns an evaluated board into the
 * Passport's tiles. Kept out of `AccountViewModel`'s collect block precisely so it can be tested
 * here, the same way [com.example.focusflight.data.model.AchievementProgress] is.
 */
class AchievementStackTest {

    private fun distance(
        id: String,
        rank: Int,
        target: Double,
        isUnlocked: Boolean,
        unlockedAt: Long? = null
    ) = AchievementStatus(
        id = id,
        category = AchievementCategory.DISTANCE,
        displayName = id,
        description = "",
        current = if (isUnlocked) target else 0.0,
        target = target,
        unitLabel = "km",
        isUnlocked = isUnlocked,
        unlockedAt = unlockedAt,
        familyId = "dist_cumulative",
        familyRank = rank
    )

    private fun behavioral(id: String, isUnlocked: Boolean, unlockedAt: Long? = null) =
        AchievementStatus(
            id = id,
            category = AchievementCategory.BEHAVIORAL,
            displayName = id,
            description = "",
            current = if (isUnlocked) 1.0 else 0.0,
            target = 1.0,
            unitLabel = "",
            isUnlocked = isUnlocked,
            unlockedAt = unlockedAt
        )

    private fun board(distance: List<AchievementStatus> = emptyList(), behavioral: List<AchievementStatus> = emptyList()) =
        AchievementBoard(geographic = emptyList(), distance = distance, behavioral = behavioral)

    @Test
    fun `a ladder family collapses into one stack topped by the furthest tier earned`() {
        val stacks = buildAchievementStacks(
            board(
                distance = listOf(
                    distance("t0", rank = 0, target = 5_000.0, isUnlocked = true, unlockedAt = 100L),
                    distance("t1", rank = 1, target = 40_075.0, isUnlocked = true, unlockedAt = 200L),
                    distance("t2", rank = 2, target = 384_400.0, isUnlocked = false),
                    distance("t3", rank = 3, target = 1_000_000.0, isUnlocked = false)
                )
            )
        )

        assertEquals(1, stacks.size)
        val stack = stacks.single()
        assertTrue(stack.isStacked)
        assertEquals("t1", stack.top.id)
        // Earned tiers only, best first. Unearned tiers are the Challenges tab's job.
        assertEquals(listOf("t1", "t0"), stack.earned.map { it.id })
    }

    @Test
    fun `a family with nothing earned is dropped entirely`() {
        // The Passport is a trophy case; a wholly-unearned family belongs on the Challenges tab.
        val stacks = buildAchievementStacks(
            board(distance = listOf(distance("t0", rank = 0, target = 5_000.0, isUnlocked = false)))
        )

        assertTrue(stacks.isEmpty())
    }

    @Test
    fun `an achievement with no family is a stack of one keyed on its own id`() {
        val stacks = buildAchievementStacks(
            board(behavioral = listOf(behavioral("behav_first_flight", isUnlocked = true, unlockedAt = 5L)))
        )

        val stack = stacks.single()
        assertEquals("behav_first_flight", stack.key)
        assertTrue(!stack.isStacked)
        assertEquals(1, stack.earned.size)
    }

    @Test
    fun `unfamilied achievements never merge with each other`() {
        // Two separate badges must stay two tiles - the null familyId groups on the id instead.
        val stacks = buildAchievementStacks(
            board(
                behavioral = listOf(
                    behavioral("behav_first_flight", isUnlocked = true, unlockedAt = 5L),
                    behavioral("behav_red_eye_pilot", isUnlocked = true, unlockedAt = 6L)
                )
            )
        )

        assertEquals(2, stacks.size)
    }

    @Test
    fun `stacks are ordered by tier then newest-earned, judged on the top card`() {
        // Distance targets over 100,000 km are GOLD; every behavioral achievement is RUBY, which
        // sorts last because it is not a rank at all.
        val stacks = buildAchievementStacks(
            board(
                distance = listOf(
                    distance("gold", rank = 0, target = 384_400.0, isUnlocked = true, unlockedAt = 10L)
                ),
                behavioral = listOf(
                    behavioral("ruby_old", isUnlocked = true, unlockedAt = 1L),
                    behavioral("ruby_new", isUnlocked = true, unlockedAt = 99L)
                )
            )
        )

        // The ladder's key is its family id; the two lone badges key on their own ids.
        assertEquals(listOf("dist_cumulative", "ruby_new", "ruby_old"), stacks.map { it.key })
        assertEquals("gold", stacks.first().top.id)
    }

    @Test
    fun `ruby sorts after every metal, however recently it was earned`() {
        // The unranked band must not jump the queue on recency alone - it is a different axis, not
        // a better one.
        val stacks = buildAchievementStacks(
            board(
                distance = listOf(
                    distance("bronze", rank = 0, target = 5_000.0, isUnlocked = true, unlockedAt = 1L)
                ),
                behavioral = listOf(behavioral("ruby", isUnlocked = true, unlockedAt = 9_999L))
            )
        )

        assertEquals(listOf("dist_cumulative", "ruby"), stacks.map { it.key })
    }
}
