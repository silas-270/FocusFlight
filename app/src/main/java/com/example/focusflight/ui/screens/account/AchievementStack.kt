package com.example.focusflight.ui.screens.account

import com.example.focusflight.data.model.AchievementBoard
import com.example.focusflight.data.model.AchievementStatus

/**
 * One Passport tile.
 *
 * Most achievements are a stack of exactly one - [earned] has a single entry and [nextLocked] is
 * null - and render as the ordinary badge they always did. A ladder family (today only the four
 * cumulative-distance milestones, see `DistanceAchievementCatalog.FAMILY_ID`) collapses into a
 * single tile showing [top], because four plaques that all say "you have flown a lot of km" is
 * three plaques of noise.
 */
data class AchievementStack(
    /** The family id when there is one, else the lone achievement's own id. Unique per tile, so
     *  it is safe as a `LazyRow` key. */
    val key: String,
    /** The furthest-along earned tier - what the tile itself shows. */
    val top: AchievementStatus,
    /** Every earned tier in the family, best first. Never empty. */
    val earned: List<AchievementStatus>
) {
    /** True when this tile should render as a stack of cards rather than a single badge. */
    val isStacked: Boolean get() = earned.size > 1
}

/**
 * Folds an evaluated board into the Passport's tiles.
 *
 * Earned achievements only, and a group with nothing earned is dropped entirely: the Passport is a
 * trophy case, and an unearned goal belongs on the Challenges screen's Achievements tab instead -
 * the two surfaces stay strictly complementary, as they were before.
 *
 * Ordering is unchanged from the flat version it replaces: hardest metal first, ruby (the unranked
 * band) last, and newest-first within a tier - all judged on [AchievementStack.top].
 *
 * Pure, and deliberately not inlined into `AccountViewModel`'s `collect` block, so it is directly
 * unit-testable - the same posture `AchievementProgress` takes for the evaluation math.
 */
fun buildAchievementStacks(board: AchievementBoard): List<AchievementStack> =
    (board.geographic + board.distance + board.behavioral)
        .filter { it.isUnlocked }
        .groupBy { it.familyId ?: it.id }
        .map { (key, members) ->
            val earned = members.sortedByDescending { it.familyRank }
            AchievementStack(key = key, top = earned.first(), earned = earned)
        }
        .sortedWith(
            compareBy<AchievementStack> { achievementTier(it.top).sortOrder }
                .thenByDescending { it.top.unlockedAt ?: Long.MIN_VALUE }
        )
