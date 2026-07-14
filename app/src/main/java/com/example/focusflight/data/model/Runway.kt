package com.example.focusflight.data.model

data class Runway(
    val runwayId: Int,
    val airportId: Int,
    val lengthFt: Float,
    val widthFt: Float,
    val leHeading: Float,
    val heHeading: Float,
    val leLat: Double,
    val leLon: Double,
    val heLat: Double,
    val heLon: Double
)
