package com.silas270.blocktime.data.model

data class FlightHighlights(
    val longestFlight: FlightLog? = null,
    val mostVisitedIata: String? = null,
    val mostVisitedCount: Int = 0,
    val equatorRatio: Double = 0.0
)
