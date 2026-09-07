package com.example.focusflight.data.model

/**
 * One member of a completable set - a continent, a country, an airport. Its [id] is whatever the
 * owning definition tests against (a continent code, an ISO country code, an IATA code) and
 * [displayName] is the human-readable label for it.
 *
 * Deliberately not challenge-specific despite originating there: both challenges (a Set-completion
 * instance, testing landed destinations against [ChallengeSetDefinition.members]) and achievements
 * (a geographic goal, testing [VisitedGeography] against a continent's country list) describe the
 * same shape, and there is no reason for two of it.
 */
data class SetMember(
    val id: String,
    val displayName: String
)

/**
 * A member's state against some progress source.
 *
 * [isVisited] is always *derived at read time*, never persisted: a challenge stores only the ids it
 * has credited ([Challenge.visitedSetMembers]) and an achievement stores nothing at all, so "what
 * is still missing" is a diff against the catalog definition rather than a second stored set that
 * could disagree with the first. See [Challenge.resolveSetMemberProgress] and
 * [GeographicAchievementGoal.memberProgress].
 */
data class SetMemberProgress(
    val id: String,
    val displayName: String,
    val isVisited: Boolean
)

/** Visited members first, so a long checklist opens on what has been earned rather than on a wall
 *  of blanks. Shared so every set surface - challenge or achievement - orders identically. */
fun List<SetMemberProgress>.visitedFirst(): List<SetMemberProgress> =
    sortedByDescending { it.isVisited }
