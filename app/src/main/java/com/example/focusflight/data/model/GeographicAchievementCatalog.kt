package com.example.focusflight.data.model

/**
 * A geographic-set achievement goal (docs/design/achievements.md's "Geographic sets" category) -
 * metadata only, no instance state. [AchievementProgress.evaluateGeographic] reads live progress
 * straight off [VisitedGeography] (itself already STORY-only filtered - see
 * `AirportRepository.getVisitedGeography`) rather than persisting anything new: there's no
 * "starts fresh" concern here the way there is for the equivalent Set-completion *challenge* type
 * (achievements.md's "Relationship to Challenges" - an achievement is meant to read persistent
 * Story Mode state, unlike a challenge). A small, real seed list, not exhaustive continent/country
 * coverage - see [GeographicAchievementCatalog.ALL].
 */
sealed class GeographicAchievementGoal(
    override val id: String,
    override val displayName: String,
    override val description: String
) : AchievementCatalogEntry {
    /** Every continent *reached* (at least one country visited in it) - the same "touched, not
     *  fully covered" bar as the equivalent Set-completion challenge's `all_continents`
     *  definition (see [CuratedChallengeSets.ALL_CONTINENTS]), not [ContinentStats.isCompleted]
     *  for every continent (that would be [AllCountries] in all but name). */
    object AllContinents : GeographicAchievementGoal(
        id = "geo_all_continents",
        displayName = "Globetrotter",
        description = "Visit at least one country on every continent."
    )

    /** Every country in the world map visited - the strictest geographic goal, distinct from
     *  [AllContinents]'s "touched" bar. */
    object AllCountries : GeographicAchievementGoal(
        id = "geo_all_countries",
        displayName = "World Traveler",
        description = "Visit every country in the world."
    )

    /** Full coverage of one specific continent - directly mirrors [ContinentStats.isCompleted]
     *  for [continentCode]. */
    class EntireContinent(val continentCode: String, continentName: String) : GeographicAchievementGoal(
        id = "geo_entire_$continentCode",
        displayName = "Master of $continentName",
        description = "Visit every country in $continentName."
    )
}

object GeographicAchievementCatalog {
    val ALL: List<GeographicAchievementGoal> = listOf(
        GeographicAchievementGoal.AllContinents,
        GeographicAchievementGoal.AllCountries,
        GeographicAchievementGoal.EntireContinent("EU", "Europe"),
        GeographicAchievementGoal.EntireContinent("AF", "Africa")
    )
}
