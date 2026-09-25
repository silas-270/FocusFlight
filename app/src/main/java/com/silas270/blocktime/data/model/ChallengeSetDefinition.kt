package com.silas270.blocktime.data.model

/**
 * A curated Set-completion definition: which members exist and how to test a landed destination
 * against them. Set-completion is curated-only (docs/challenges.md - "unlike Distance's
 * target number, there's no cheap way to let a player define their own"), so [CuratedChallengeSets]
 * is the only source of Set-completion challenges; there is no custom-authored equivalent.
 */
data class ChallengeSetDefinition(
    val catalogId: String,
    val displayName: String,
    val memberKind: SetMemberKind,
    val memberItems: List<SetMember>
) {
    val members: Set<String> = memberItems.map { it.id }.toSet()
}

/**
 * A handful of real seed Set-completion definitions - enough to be genuinely testable, not
 * exhaustive content authoring (that's later content work, not this phase's job).
 */
object CuratedChallengeSets {
    /** The six inhabited continents, matching what `AirportRepository.getContinentCountryMap()`
     *  keys by. Antarctica is left out on purpose: no Antarctic airport has a route, so a set that
     *  included it could never complete. Rows started while it was a member still resolve against
     *  this definition - see `LocalChallengeRepository`'s set handling. */
    val ALL_CONTINENTS = ChallengeSetDefinition(
        catalogId = "all_continents",
        displayName = "Visit All Continents",
        memberKind = SetMemberKind.CONTINENT,
        memberItems = listOf(
            SetMember("AF", "Africa"),
            SetMember("AS", "Asia"),
            SetMember("EU", "Europe"),
            SetMember("NA", "North America"),
            SetMember("OC", "Oceania"),
            SetMember("SA", "South America")
        )
    )

    /** London, Ottawa, Paris, Berlin, Rome, Tokyo, Washington D.C. (nearest major IATA code per
     *  capital). */
    val G7_CAPITALS = ChallengeSetDefinition(
        catalogId = "g7_capitals",
        displayName = "Visit Every G7 Capital",
        memberKind = SetMemberKind.IATA,
        memberItems = listOf(
            SetMember("BER", "Berlin (Germany)"),
            SetMember("CDG", "Paris (France)"),
            SetMember("FCO", "Rome (Italy)"),
            SetMember("HND", "Tokyo (Japan)"),
            SetMember("IAD", "Washington D.C. (USA)"),
            SetMember("LHR", "London (UK)"),
            SetMember("YOW", "Ottawa (Canada)")
        )
    )

    val EUROPEAN_EXPLORER = ChallengeSetDefinition(
        catalogId = "european_explorer",
        displayName = "European Explorer",
        memberKind = SetMemberKind.COUNTRY,
        memberItems = listOf(
            SetMember("FR", "France"),
            SetMember("DE", "Germany"),
            SetMember("IT", "Italy"),
            SetMember("ES", "Spain"),
            SetMember("GB", "United Kingdom")
        )
    )

    val ASIAN_ODYSSEY = ChallengeSetDefinition(
        catalogId = "asian_odyssey",
        displayName = "Asian Odyssey",
        memberKind = SetMemberKind.COUNTRY,
        memberItems = listOf(
            SetMember("CN", "China"),
            SetMember("IN", "India"),
            SetMember("JP", "Japan"),
            SetMember("SG", "Singapore"),
            SetMember("AE", "United Arab Emirates")
        )
    )

    val AFRICAN_SAFARI = ChallengeSetDefinition(
        catalogId = "african_safari",
        displayName = "African Safari",
        memberKind = SetMemberKind.COUNTRY,
        memberItems = listOf(
            SetMember("EG", "Egypt"),
            SetMember("ET", "Ethiopia"),
            SetMember("KE", "Kenya"),
            SetMember("MA", "Morocco"),
            SetMember("ZA", "South Africa")
        )
    )

    val NORTH_AMERICAN_TOUR = ChallengeSetDefinition(
        catalogId = "north_american_tour",
        displayName = "North American Tour",
        memberKind = SetMemberKind.COUNTRY,
        memberItems = listOf(
            SetMember("CA", "Canada"),
            SetMember("CR", "Costa Rica"),
            SetMember("MX", "Mexico"),
            SetMember("PA", "Panama"),
            SetMember("US", "United States")
        )
    )

    val SOUTH_AMERICAN_DISCOVERY = ChallengeSetDefinition(
        catalogId = "south_american_discovery",
        displayName = "South American Discovery",
        memberKind = SetMemberKind.COUNTRY,
        memberItems = listOf(
            SetMember("AR", "Argentina"),
            SetMember("BR", "Brazil"),
            SetMember("CL", "Chile"),
            SetMember("CO", "Colombia"),
            SetMember("PE", "Peru")
        )
    )

    val PACIFIC_ISLAND_HOPPER = ChallengeSetDefinition(
        catalogId = "pacific_island_hopper",
        displayName = "Pacific Island Hopper",
        memberKind = SetMemberKind.COUNTRY,
        memberItems = listOf(
            SetMember("AU", "Australia"),
            SetMember("FJ", "Fiji"),
            SetMember("PF", "French Polynesia"),
            SetMember("NZ", "New Zealand"),
            SetMember("PG", "Papua New Guinea")
        )
    )

    val ALL: List<ChallengeSetDefinition> = listOf(
        ALL_CONTINENTS,
        G7_CAPITALS,
        EUROPEAN_EXPLORER,
        ASIAN_ODYSSEY,
        AFRICAN_SAFARI,
        NORTH_AMERICAN_TOUR,
        SOUTH_AMERICAN_DISCOVERY,
        PACIFIC_ISLAND_HOPPER
    )

    fun find(catalogId: String): ChallengeSetDefinition? = ALL.find { it.catalogId == catalogId }
}
