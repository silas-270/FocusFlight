package com.example.focusflight.data.model

/**
 * A curated challenge template a player can start (from the Phase 3b quest log, not built yet) -
 * name/description/definition, but no instance state: `ChallengeRepository.startCuratedChallenge`
 * assigns an id, position pointer (Route), and zeroed progress when it instantiates a [Challenge]
 * row from one of these. A handful of real examples per type, per docs/design/challenges.md's
 * "What to build" - not exhaustive content, that's later authoring work.
 */
data class CuratedChallengeTemplate(
    val catalogId: String,
    val name: String,
    val description: String,
    val type: ChallengeType,
    val originIata: String? = null,
    val destIata: String? = null,
    val setDefinition: ChallengeSetDefinition? = null,
    val targetDistanceKm: Double? = null
)

object CuratedChallengeCatalog {
    val ALL: List<CuratedChallengeTemplate> = listOf(
        CuratedChallengeTemplate(
            catalogId = "route_lhr_syd",
            name = "London → Sydney",
            description = "Chart real onward connections from Heathrow all the way to Sydney.",
            type = ChallengeType.ROUTE,
            originIata = "LHR",
            destIata = "SYD"
        ),
        CuratedChallengeTemplate(
            catalogId = "route_jfk_hnd",
            name = "New York → Tokyo",
            description = "Find a real routing from JFK to Haneda.",
            type = ChallengeType.ROUTE,
            originIata = "JFK",
            destIata = "HND"
        ),
        CuratedChallengeTemplate(
            catalogId = "set_all_continents",
            name = CuratedChallengeSets.ALL_CONTINENTS.displayName,
            description = "Land on all seven continents.",
            type = ChallengeType.SET_COMPLETION,
            setDefinition = CuratedChallengeSets.ALL_CONTINENTS
        ),
        CuratedChallengeTemplate(
            catalogId = "set_g7_capitals",
            name = CuratedChallengeSets.G7_CAPITALS.displayName,
            description = "Visit the capital of every G7 nation.",
            type = ChallengeType.SET_COMPLETION,
            setDefinition = CuratedChallengeSets.G7_CAPITALS
        ),
        CuratedChallengeTemplate(
            catalogId = "distance_10000km",
            name = "10,000 km Club",
            description = "Fly a cumulative 10,000 km under this challenge.",
            type = ChallengeType.DISTANCE,
            targetDistanceKm = 10_000.0
        ),
        CuratedChallengeTemplate(
            catalogId = "distance_25000km",
            name = "Around the World",
            description = "Fly a cumulative 25,000 km - roughly the Earth's circumference.",
            type = ChallengeType.DISTANCE,
            targetDistanceKm = 25_000.0
        )
    )

    fun find(catalogId: String): CuratedChallengeTemplate? = ALL.find { it.catalogId == catalogId }
}
