package com.example.focusflight.data.model

data class FlightRoute(
    val id: Int,
    val originIata: String,
    val destIata: String,
    val distanceKm: Double,
    val durationMin: Int,
    val carriers: String,
    val destName: String,
    val destMunicipality: String,
    val destCountry: String,
    val destLat: Double,
    val destLon: Double
)
