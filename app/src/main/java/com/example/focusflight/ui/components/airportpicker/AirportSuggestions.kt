package com.example.focusflight.ui.components.airportpicker

import com.example.focusflight.data.model.Airport
import com.example.focusflight.data.repository.AirportRepository

/**
 * IATA codes for the app's shared pre-search suggestion tiles. The same four airports are shown
 * in Onboarding, Free Flight's origin picker, and Change Home Base, so the picker looks identical
 * across every flow that uses [com.example.focusflight.ui.components.airportpicker.AirportSearchStep].
 */
private val SUGGESTED_IATA_CODES = listOf("FRA", "LHR", "BER", "MUC")

/**
 * Resolves the shared suggestion IATA codes to real [Airport] rows, for callers (Free Flight's
 * origin picker, Change Home Base) that need an actual [Airport] to hand to the confirm-on-map
 * step rather than committing a selection immediately - unlike Onboarding's own
 * `selectAirportByIata`, this does not also kick off a Cesium map pre-render, since neither
 * caller needs one at suggestion-selection time. Call from an IO-dispatched coroutine.
 */
fun resolveSuggestedAirports(repository: AirportRepository): List<Airport> =
    SUGGESTED_IATA_CODES.mapNotNull { repository.getAirportByIata(it) }
