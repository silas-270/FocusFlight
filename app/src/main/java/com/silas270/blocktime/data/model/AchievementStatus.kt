package com.silas270.blocktime.data.model

/**
 * Which of docs/achievements.md's four v1 category types this achievement belongs to.
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
 *
 * [members], [familyId] and [familyRank] are the structural payload, carried as nullable fields on
 * this one shape rather than split into per-shape subclasses - the same call the [Challenge] entity
 * makes for its four types, and for the same reason: every consumer wants one list to sort, filter
 * and render.
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
    val unlockedAt: Long? = null,
    /** Non-null exactly for set-shaped achievements (today: every [GeographicAchievementGoal]).
     *  Derived on every read from [VisitedGeography] and never persisted - see
     *  [GeographicAchievementGoal.memberProgress]. Null means "this goal is a scalar, not a set",
     *  which is what a UI should branch on before offering a checklist. */
    val members: List<SetMemberProgress>? = null,
    /** Ladder identity. Non-null only where the entries genuinely supersede one another, so the
     *  Passport can collapse them into a single stacked tile instead of showing near-identical
     *  plaques side by side. Parallel siblings ("Master of Europe" / "Master of Africa") share no
     *  family: neither one is a better version of the other. */
    val familyId: String? = null,
    /** Position within [familyId]'s ladder; higher is further along. Meaningless, and always 0,
     *  when [familyId] is null. */
    val familyRank: Int = 0
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
    isUnlocked: Boolean,
    members: List<SetMemberProgress>? = null,
    familyId: String? = null,
    familyRank: Int = 0
): AchievementStatus = AchievementStatus(
    id = id,
    category = category,
    displayName = displayName,
    description = description,
    current = current,
    target = target,
    unitLabel = unitLabel,
    isUnlocked = isUnlocked,
    members = members,
    familyId = familyId,
    familyRank = familyRank
)
