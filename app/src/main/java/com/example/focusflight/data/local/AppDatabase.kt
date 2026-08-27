package com.example.focusflight.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.focusflight.data.model.FlightLog
import com.example.focusflight.data.model.UserProfile

@Database(
    entities = [UserProfile::class, FlightLog::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userProfileDao(): UserProfileDao
    abstract fun flightLogDao(): FlightLogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /** Adds the mode-tag column (see FlightMode/docs/design/mechanics.md). Every row that
         *  existed before this column was added represents pre-mode-tag Story Mode play, so it
         *  backfills to 'STORY' rather than left null - matching FlightLog.mode's own default. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE flight_log ADD COLUMN mode TEXT NOT NULL DEFAULT 'STORY'"
                )
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "user_data.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
