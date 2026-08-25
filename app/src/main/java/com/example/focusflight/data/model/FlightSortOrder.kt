package com.example.focusflight.data.model

enum class FlightSortOrder(val displayName: String) {
    DATE_DESC("Newest First"),
    DATE_ASC("Oldest First"),
    DISTANCE_DESC("Longest Distance"),
    DISTANCE_ASC("Shortest Distance"),
    DURATION_DESC("Longest Duration")
}
