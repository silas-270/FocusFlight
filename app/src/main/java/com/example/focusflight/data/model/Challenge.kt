package com.example.focusflight.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One row per challenge instance (curated or custom, any of the three types) - a single store,
 * not per-type tables, per docs/design/challenges.md#persistence--route-scoping. Which fields
 * are meaningful depends on [type]:
 *
 * - ROUTE: [originIata] (fixed - the progress formula's denominator), [destIata] (the win
 *   condition), [positionIata] (the pointer - starts equal to [originIata], moves on landing;
 *   this is what a CHALLENGE-tagged session's FlightSearch/CheckIn/InFlight read as origin
 *   instead of `currentAirport`), [routeProgressFraction] (cached, recomputed on every move by
 *   [ChallengeProgress.routeProgress]).
 * - SET_COMPLETION: [setCatalogId]/[setMemberKind]/[setTotalMembers] identify which curated
 *   [ChallengeSetDefinition] this instance is tracking and how to test membership;
 *   [visitedSetMembers] is *this instance's own* tracked set - starts empty when the challenge is
 *   started and is never seeded from Story Mode's visited-set (challenges.md#isolation - this is
 *   the detail that makes a challenge repeatable, unlike an achievement).
 * - DISTANCE: [targetDistanceKm] / [cumulativeDistanceKm].
 *
 * [source] and [status] apply to every type. The 3-active cap and "abandon deletes the row
 * entirely" live in `ChallengeRepository`, not here - this class is pure storage shape.
 */
@Entity(
    tableName = "challenges",
    foreignKeys = [
        ForeignKey(
            entity = UserProfile::class,
            parentColumns = ["id"],
            childColumns = ["user_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["user_id"]), Index(value = ["user_id", "status"])]
)
data class Challenge(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "user_id") val userId: Int,
    val type: ChallengeType,
    val source: ChallengeSource,
    val status: ChallengeStatus = ChallengeStatus.ACTIVE,
    val name: String,
    val description: String = "",

    // ── Route only ──────────────────────────────────────────────────────────────────────
    @ColumnInfo(name = "origin_iata") val originIata: String? = null,
    @ColumnInfo(name = "dest_iata") val destIata: String? = null,
    @ColumnInfo(name = "position_iata") val positionIata: String? = null,
    @ColumnInfo(name = "route_progress_fraction") val routeProgressFraction: Float = 0f,

    // ── Set-completion only ─────────────────────────────────────────────────────────────
    @ColumnInfo(name = "set_catalog_id") val setCatalogId: String? = null,
    @ColumnInfo(name = "set_member_kind") val setMemberKind: SetMemberKind? = null,
    @ColumnInfo(name = "set_total_members") val setTotalMembers: Int = 0,
    @ColumnInfo(name = "set_visited_members") val visitedSetMembers: Set<String> = emptySet(),

    // ── Distance only ───────────────────────────────────────────────────────────────────
    @ColumnInfo(name = "target_distance_km") val targetDistanceKm: Double? = null,
    @ColumnInfo(name = "cumulative_distance_km") val cumulativeDistanceKm: Double = 0.0,

    @ColumnInfo(name = "started_at") val startedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "completed_at") val completedAt: Long? = null
)
