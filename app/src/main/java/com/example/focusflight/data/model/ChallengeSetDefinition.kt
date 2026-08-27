package com.example.focusflight.data.model

/**
 * A curated Set-completion definition: which members exist and how to test a landed destination
 * against them. Set-completion is curated-only (docs/design/challenges.md - "unlike Distance's
 * target number, there's no cheap way to let a player define their own"), so [CuratedChallengeSets]
 * is the only source of Set-completion challenges; there is no custom-authored equivalent.
 */
data class ChallengeSetDefinition(
    val catalogId: String,
    val displayName: String,
    val memberKind: SetMemberKind,
    val members: Set<String>
)

/**
 * A handful of real seed Set-completion definitions - enough to be genuinely testable, not
 * exhaustive content authoring (that's later content work, not this phase's job).
 */
object CuratedChallengeSets {
    /** The seven standard continent codes, matching what
     *  `AirportRepository.getContinentCountryMap()` already keys by. */
    val ALL_CONTINENTS = ChallengeSetDefinition(
        catalogId = "all_continents",
        displayName = "Visit All Continents",
        memberKind = SetMemberKind.CONTINENT,
        members = setOf("AF", "AN", "AS", "EU", "NA", "OC", "SA")
    )

    /** London, Ottawa, Paris, Berlin, Rome, Tokyo, Washington D.C. (nearest major IATA code per
     *  capital). */
    val G7_CAPITALS = ChallengeSetDefinition(
        catalogId = "g7_capitals",
        displayName = "Visit Every G7 Capital",
        memberKind = SetMemberKind.IATA,
        members = setOf("LHR", "YOW", "CDG", "BER", "FCO", "HND", "IAD")
    )

    val ALL: List<ChallengeSetDefinition> = listOf(ALL_CONTINENTS, G7_CAPITALS)

    fun find(catalogId: String): ChallengeSetDefinition? = ALL.find { it.catalogId == catalogId }
}
