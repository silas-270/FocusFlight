package com.silas270.blocktime.data.local

import androidx.room.TypeConverter
import com.silas270.blocktime.data.model.ChallengeSource
import com.silas270.blocktime.data.model.ChallengeStatus
import com.silas270.blocktime.data.model.ChallengeType
import com.silas270.blocktime.data.model.FlightMode
import com.silas270.blocktime.data.model.PausedFlight
import com.silas270.blocktime.data.model.SetMemberKind

/**
 * Room [TypeConverter]s for enum (and one Set<String>) columns: [FlightMode] (`FlightLog.mode`)
 * plus the `Challenge` entity's [ChallengeType]/[ChallengeSource]/[ChallengeStatus]/
 * [SetMemberKind] and its `visitedSetMembers` field. Enums are stored as their plain
 * enum-constant name - the simplest representation for a small, stable set of values.
 */
class Converters {
    @TypeConverter
    fun fromFlightMode(mode: FlightMode): String = mode.name

    @TypeConverter
    fun toFlightMode(value: String): FlightMode =
        runCatching { FlightMode.valueOf(value) }.getOrDefault(FlightMode.STORY)

    @TypeConverter
    fun fromChallengeType(type: ChallengeType): String = type.name

    @TypeConverter
    fun toChallengeType(value: String): ChallengeType =
        runCatching { ChallengeType.valueOf(value) }.getOrDefault(ChallengeType.DISTANCE)

    @TypeConverter
    fun fromChallengeSource(source: ChallengeSource): String = source.name

    @TypeConverter
    fun toChallengeSource(value: String): ChallengeSource =
        runCatching { ChallengeSource.valueOf(value) }.getOrDefault(ChallengeSource.CUSTOM)

    @TypeConverter
    fun fromChallengeStatus(status: ChallengeStatus): String = status.name

    @TypeConverter
    fun toChallengeStatus(value: String): ChallengeStatus =
        runCatching { ChallengeStatus.valueOf(value) }.getOrDefault(ChallengeStatus.ACTIVE)

    @TypeConverter
    fun fromSetMemberKind(kind: SetMemberKind?): String? = kind?.name

    @TypeConverter
    fun toSetMemberKind(value: String?): SetMemberKind? =
        value?.let { runCatching { SetMemberKind.valueOf(it) }.getOrNull() }

    /** No member value in play (continent/country/IATA codes) can ever contain a comma, so a
     *  plain join/split is safe and keeps this consistent with the rest of this file's
     *  simplest-representation approach. */
    @TypeConverter
    fun fromStringSet(value: Set<String>): String = value.joinToString(",")

    @TypeConverter
    fun toStringSet(value: String): Set<String> =
        if (value.isBlank()) emptySet() else value.split(",").toSet()

    /** `Challenge.pausedFlight` - the same pipe-delimited format
     *  [PreferencesRepository][com.silas270.blocktime.data.repository.PreferencesRepository]
     *  uses for the Story/Free slot, so there's one (de)serialization to trust, not two. */
    @TypeConverter
    fun fromPausedFlight(value: PausedFlight?): String? = value?.serialize()

    @TypeConverter
    fun toPausedFlight(value: String?): PausedFlight? = value?.let { PausedFlight.parse(it) }
}
