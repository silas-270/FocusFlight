package com.example.focusflight.data.model

/**
 * Which of docs/design/achievements.md's four v1 category types this achievement belongs to.
 * "Challenges completed" isn't a member of this enum - it's a flat log
 * (see `ChallengeRepository.listCompletedChallenges`), not a progress-bar category, so it has no
 * [AchievementStatus] representation at all.
 */
enum class AchievementCategory { GEOGRAPHIC, DISTANCE, BEHAVIORAL }

/**
 * One evaluated achievement, always fully populated - "locked (not-yet-earned) achievements are
 * fully visible with progress shown", per achievements.md's "Reveal style". There is no separate
 * hidden/mystery variant; every achievement is represented by exactly this shape whether or not
 * [isUnlocked] is true.
 *
 * [current]/[target] share the unit named by [unitLabel] (e.g. "continents", "km"). A
 * boolean-style achievement (e.g. "First Flight") uses [target] == 1.0 and [current] of 0.0 or
 * 1.0 with an empty [unitLabel] - callers rendering a value string should special-case
 * `target <= 1.0` rather than printing "0 / 1".
 *
 * [unlockedAt] is the one field [AchievementProgress] does *not* compute: evaluation stays pure
 * and always leaves it null, and `AchievementsRepository` fills it in afterwards from the
 * `achievement_unlocks` table. Treat a null here as "unlock time unknown", never as "locked" -
 * [isUnlocked] is the only authority on that.
 */
data class AchievementStatus(
    val id: String,
    val category: AchievementCategory,
    val displayName: String,
    val description: String,
    val current: Double,
    val target: Double,
    val unitLabel: String,
    val isUnlocked: Boolean,
    val unlockedAt: Long? = null
) {
    val progress: Float
        get() = if (target > 0.0) (current / target).toFloat().coerceIn(0f, 1f) else if (isUnlocked) 1f else 0f
}

/**
 * The `id`/`displayName`/`description` metadata shared by every achievement catalog entry
 * ([DistanceAchievementMilestone], [GeographicAchievementGoal]) - lets [toStatus] carry that
 * copy-through once instead of [AchievementProgress] repeating it at each evaluation site.
 */
interface AchievementCatalogEntry {
    val id: String
    val displayName: String
    val description: String
}

fun AchievementCatalogEntry.toStatus(
    category: AchievementCategory,
    current: Double,
    target: Double,
    unitLabel: String,
    isUnlocked: Boolean
): AchievementStatus = AchievementStatus(
    id = id,
    category = category,
    displayName = displayName,
    description = description,
    current = current,
    target = target,
    unitLabel = unitLabel,
    isUnlocked = isUnlocked
)
