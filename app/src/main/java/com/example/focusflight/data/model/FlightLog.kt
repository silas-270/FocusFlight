package com.example.focusflight.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "flight_log",
    foreignKeys = [
        ForeignKey(
            entity = UserProfile::class,
            parentColumns = ["id"],
            childColumns = ["user_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["user_id"])]
)
data class FlightLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "user_id") val userId: Int,
    @ColumnInfo(name = "flight_number") val flightNumber: String,
    @ColumnInfo(name = "origin_iata") val originIata: String,
    @ColumnInfo(name = "dest_iata") val destIata: String,
    @ColumnInfo(name = "duration_min") val durationMin: Int,
    @ColumnInfo(name = "distance_km") val distanceKm: Double,
    @ColumnInfo(name = "completed_at") val completedAt: Long,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)
