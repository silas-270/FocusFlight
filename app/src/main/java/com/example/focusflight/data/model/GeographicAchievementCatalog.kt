package com.example.focusflight.data.model

import com.example.focusflight.util.countryDisplayName

/**
 * A geographic-set achievement goal (docs/achievements.md's "Geographic sets" category) -
 * metadata only, no instance state. [AchievementProgress.evaluateGeographic] reads live progress
 * straight off [VisitedGeography] (itself already STORY-only filtered - see
 * `AirportRepository.getVisitedGeography`) rather than persisting anything new: there's no
 * "starts fresh" concern here the way there is for the equivalent Set-completion *challenge* type
 * (achievements.md's "Relationship to Challenges" - an achievement is meant to read persistent
 * Story Mode state, unlike a challenge). A small, real seed list, not exhaustive continent/country
 * coverage - see [GeographicAchievementCatalog.ALL].
 *
 * Every goal here must be able to enumerate its own members, not just count them. That obligation
 * is [memberProgress], and it is abstract on purpose: a geographic goal is a *set*, and one that
 * could only report `12 / 54` would be exactly the dead end this abstract member exists to prevent.
 * The scalar progress bar is then a derived summary of the checklist rather than the only thing
 * the goal knows about itself.
 */
sealed class GeographicAchievementGoal(
    override val id: String,
    override val displayName: String,
    override val description: String
) : AchievementCatalogEntry {

    /**
     * The goal's full member list with live visited state, visited-first.
     *
     * Nothing here is persisted: "missing" is always this list minus what [geo] reports, computed
     * on every read, mirroring [Challenge.resolveSetMemberProgress]'s derivation for the challenge
     * side. Returns an empty list when [geo] carries nothing to say about the goal (an unknown
     * continent code, a pre-onboarding empty geography) - the same non-crashing 0/0 posture
     * [AchievementProgress.evaluateGeographic] already takes.
     */
    abstract fun memberProgress(geo: VisitedGeography): List<SetMemberProgress>

    /** Every continent *reached* (at least one country visited in it) - the same "touched, not
     *  fully covered" bar as the equivalent Set-completion challenge's `all_continents`
     *  definition (see [CuratedChallengeSets.ALL_CONTINENTS]), not [ContinentStats.isCompleted]
     *  for every continent (that would be [AllCountries] in all but name). */
    object AllContinents : GeographicAchievementGoal(
        id = "geo_all_continents",
        displayName = "Globetrotter",
        description = "Visit at least one country on every inhabited continent."
    ) {
        /** Reuses [CuratedChallengeSets.ALL_CONTINENTS]'s member labels rather than declaring a
         *  second continent-name table, and lists only continents present in [geo] - the same
         *  ones the progress target counts, so the checklist and "6 / 6" always agree. */
        override fun memberProgress(geo: VisitedGeography): List<SetMemberProgress> =
            CuratedChallengeSets.ALL_CONTINENTS.memberItems
                .filter { member -> geo.continentStats.any { it.continentCode == member.id } }
                .map { member ->
                    SetMemberProgress(
                        id = member.id,
                        displayName = member.displayName,
                        isVisited = member.id in geo.reachedContinents
                    )
                }.visitedFirst()
    }

    /** Every country the route network reaches visited - the strictest geographic goal,
     *  distinct from [AllContinents]'s "touched" bar. */
    object AllCountries : GeographicAchievementGoal(
        id = "geo_all_countries",
        displayName = "World Traveler",
        description = "Visit every country you can fly to."
    ) {
        override fun memberProgress(geo: VisitedGeography): List<SetMemberProgress> =
            geo.countryToContinent.keys.map { code ->
                SetMemberProgress(
                    id = code,
                    displayName = countryDisplayName(code),
                    isVisited = code in geo.visitedCountries
                )
            }.sortedBy { it.displayName }.visitedFirst()
    }

    /** Full coverage of one specific continent - directly mirrors [ContinentStats.isCompleted]
     *  for [continentCode]. */
    class EntireContinent(val continentCode: String, continentName: String) : GeographicAchievementGoal(
        id = "geo_entire_$continentCode",
        displayName = "Master of $continentName",
        description = "Visit every country in $continentName."
    ) {
        /** [ContinentStats] already carries both halves of the answer - `visitedCountries` and
         *  `missingCountries` - so this is a straight map of the two rather than a diff. */
        override fun memberProgress(geo: VisitedGeography): List<SetMemberProgress> {
            val stat = geo.continentStats.find { it.continentCode == continentCode } ?: return emptyList()
            fun member(code: String, visited: Boolean) =
                SetMemberProgress(id = code, displayName = countryDisplayName(code), isVisited = visited)
            return (stat.visitedCountries.map { member(it, true) } +
                stat.missingCountries.map { member(it, false) })
                .sortedBy { it.displayName }
                .visitedFirst()
        }
    }
    /** Milestone for unique country count visited across the world. */
    class CountryCountMilestone(
        id: String,
        displayName: String,
        description: String,
        val targetCount: Int,
        val familyRank: Int
    ) : GeographicAchievementGoal(id, displayName, description) {
        override fun memberProgress(geo: VisitedGeography): List<SetMemberProgress> =
            geo.countryToContinent.keys.map { code ->
                SetMemberProgress(
                    id = code,
                    displayName = countryDisplayName(code),
                    isVisited = code in geo.visitedCountries
                )
            }.sortedBy { it.displayName }.visitedFirst()
    }
}

object GeographicAchievementCatalog {
    const val COUNTRY_FAMILY_ID = "geo_country_count"

    val ALL: List<GeographicAchievementGoal> = listOf(
        // Country Explorer Ladder
        GeographicAchievementGoal.CountryCountMilestone(
            id = "geo_10_border_crosser",
            displayName = "Border Crosser",
            description = "Visit 10 unique countries in Story Mode.",
            targetCount = 10,
            familyRank = 0
        ),
        GeographicAchievementGoal.CountryCountMilestone(
            id = "geo_50_diplomat",
            displayName = "Diplomat",
            description = "Visit 50 unique countries in Story Mode.",
            targetCount = 50,
            familyRank = 1
        ),
        GeographicAchievementGoal.CountryCountMilestone(
            id = "geo_100_world_citizen",
            displayName = "World Citizen",
            description = "Visit 100 unique countries in Story Mode.",
            targetCount = 100,
            familyRank = 2
        ),
        GeographicAchievementGoal.AllCountries,

        // Global & Continental sets
        GeographicAchievementGoal.AllContinents,
        GeographicAchievementGoal.EntireContinent("EU", "Europe"),
        GeographicAchievementGoal.EntireContinent("AS", "Asia"),
        GeographicAchievementGoal.EntireContinent("AF", "Africa"),
        GeographicAchievementGoal.EntireContinent("NA", "North America"),
        GeographicAchievementGoal.EntireContinent("SA", "South America"),
        GeographicAchievementGoal.EntireContinent("OC", "Oceania")
        // No "Master of Antarctica": no Antarctic airport has a single route, so it could only
        // ever show LOCKED with an empty checklist.
    )
}
