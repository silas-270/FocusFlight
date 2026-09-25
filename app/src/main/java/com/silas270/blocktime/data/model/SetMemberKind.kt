package com.silas270.blocktime.data.model

/**
 * How a Set-completion challenge tests whether a landed destination touches a new set member -
 * reuses whatever geography data [Airport]/`AirportRepository` already expose (per
 * docs/challenges.md, curated Set-completion definitions are the authoring seam, not a
 * new data source).
 */
enum class SetMemberKind {
    /** Member value = the destination airport's continent code (e.g. "EU"). Backs "visit all
     *  continents". */
    CONTINENT,

    /** Member value = the destination airport's ISO country code. */
    COUNTRY,

    /** Member value = the destination airport's own IATA code - for a curated list of named
     *  airports (e.g. "every G7 capital"). */
    IATA
}
