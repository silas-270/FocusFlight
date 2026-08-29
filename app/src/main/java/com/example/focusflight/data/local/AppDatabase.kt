package com.example.focusflight.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.focusflight.data.model.AchievementUnlock
import com.example.focusflight.data.model.Challenge
import com.example.focusflight.data.model.FlightLog
import com.example.focusflight.data.model.UserProfile

@Database(
    entities = [UserProfile::class, FlightLog::class, Challenge::class, AchievementUnlock::class],
    version = 6,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userProfileDao(): UserProfileDao
    abstract fun flightLogDao(): FlightLogDao
    abstract fun challengeDao(): ChallengeDao
    abstract fun achievementUnlockDao(): AchievementUnlockDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "user_data.db"
                )
                    // No hand-written migrations. This app has only ever been installed on one
                    // device, so there is no existing install whose data a schema change has to
                    // preserve - and carrying five migrations for a case that cannot occur was
                    // pure weight. A schema change now wipes and rebuilds instead of crashing on a
                    // missing migration path.
                    //
                    // This must be revisited before the app is ever distributed: from the first
                    // real user onward, a destructive fallback silently deletes their entire
                    // logbook and challenge progress on any schema bump. `version` stays at 6 so
                    // this device is not seen as a downgrade.
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
