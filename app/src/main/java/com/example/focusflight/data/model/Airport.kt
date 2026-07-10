package com.example.focusflight.data.model

data class Airport(
    val id: Int,
    val ident: String,
    val iataCode: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    val elevationFt: Double,
    val continent: String,
    val isoCountry: String,
    val isoRegion: String,
    val municipality: String,
    val type: String
)
