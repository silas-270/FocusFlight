package com.example.focusflight.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.focusflight.data.model.FlightLog
import com.example.focusflight.data.model.UserProfile

@Database(
    entities = [UserProfile::class, FlightLog::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userProfileDao(): UserProfileDao
    abstract fun flightLogDao(): FlightLogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "user_data.db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
