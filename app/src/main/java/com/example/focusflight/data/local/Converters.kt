package com.example.focusflight.data.local

import androidx.room.TypeConverter
import com.example.focusflight.data.model.FlightMode

/**
 * Room [TypeConverter]s for enum columns. Currently just [FlightMode]
 * (`FlightLog.mode`) - stored as its plain enum-constant name, the simplest
 * representation for a small, stable set of values.
 */
class Converters {
    @TypeConverter
    fun fromFlightMode(mode: FlightMode): String = mode.name

    @TypeConverter
    fun toFlightMode(value: String): FlightMode =
        runCatching { FlightMode.valueOf(value) }.getOrDefault(FlightMode.STORY)
}
