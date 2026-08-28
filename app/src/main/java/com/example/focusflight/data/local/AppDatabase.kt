package com.example.focusflight.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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

        /** Adds the single challenges store (all three types, curated and custom alike) - see
         *  Challenge/docs/design/challenges.md#persistence--route-scoping. Brand-new table, no
         *  pre-existing rows to backfill, so every column is a plain NOT NULL/nullable per the
         *  entity's own Kotlin type - no SQL-level DEFAULT is needed since Room always supplies
         *  every column on insert. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `challenges` (
                        `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        `user_id` INTEGER NOT NULL,
                        `type` TEXT NOT NULL,
                        `source` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `origin_iata` TEXT,
                        `dest_iata` TEXT,
                        `position_iata` TEXT,
                        `route_progress_fraction` REAL NOT NULL,
                        `set_catalog_id` TEXT,
                        `set_member_kind` TEXT,
                        `set_total_members` INTEGER NOT NULL,
                        `set_visited_members` TEXT NOT NULL,
                        `target_distance_km` REAL,
                        `cumulative_distance_km` REAL NOT NULL,
                        `started_at` INTEGER NOT NULL,
                        `completed_at` INTEGER,
                        FOREIGN KEY(`user_id`) REFERENCES `user_profile`(`id`) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_challenges_user_id` ON `challenges` (`user_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_challenges_user_id_status` ON `challenges` (`user_id`, `status`)")
            }
        }

        /** Adds the achievement-unlock timestamp store (see AchievementUnlock) - the only
         *  persisted achievement state, used purely to order earned badges newest-first.
         *  Brand-new table, so nothing to backfill: achievements already unlocked before this
         *  migration get stamped lazily the first time an achievements surface is opened, which
         *  is the closest to a true unlock moment the data model can recover. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `achievement_unlocks` (
                        `user_id` INTEGER NOT NULL,
                        `achievement_id` TEXT NOT NULL,
                        `unlocked_at` INTEGER NOT NULL,
                        PRIMARY KEY(`user_id`, `achievement_id`),
                        FOREIGN KEY(`user_id`) REFERENCES `user_profile`(`id`) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_achievement_unlocks_user_id` ON `achievement_unlocks` (`user_id`)")
            }
        }

        /** Adds a Route challenge's own paused-flight fields (see Challenge's doc) - lets a
         *  challenge-scoped in-progress flight be resumed independent of Story Mode's and every
         *  other challenge's, instead of all sharing PreferencesRepository's single global slot.
         *  All three nullable, defaulting to null (no paused flight) for every pre-existing row. */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE challenges ADD COLUMN paused_flight_number TEXT")
                db.execSQL("ALTER TABLE challenges ADD COLUMN paused_dest_iata TEXT")
                db.execSQL("ALTER TABLE challenges ADD COLUMN paused_duration_min INTEGER")
            }
        }

        /** Replaces MIGRATION_4_5's three loose paused-flight columns with one `paused_flight`
         *  column holding a serialized [com.example.focusflight.data.model.PausedFlight] (see
         *  Challenge's doc and Converters.fromPausedFlight/toPausedFlight) - one owner of "the
         *  paused flight" instead of three independently-nullable fields that could drift out of
         *  sync with each other. SQLite has no portable DROP COLUMN across the API levels this
         *  app supports, so this is the standard recreate-copy-drop-rename recipe. The old three
         *  columns' data (always a mid-flight, transient test session) isn't carried over - every
         *  pre-existing row's new `paused_flight` starts NULL, same as a fresh install. */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE `challenges_new` (
                        `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        `user_id` INTEGER NOT NULL,
                        `type` TEXT NOT NULL,
                        `source` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `origin_iata` TEXT,
                        `dest_iata` TEXT,
                        `position_iata` TEXT,
                        `route_progress_fraction` REAL NOT NULL,
                        `set_catalog_id` TEXT,
                        `set_member_kind` TEXT,
                        `set_total_members` INTEGER NOT NULL,
                        `set_visited_members` TEXT NOT NULL,
                        `target_distance_km` REAL,
                        `cumulative_distance_km` REAL NOT NULL,
                        `paused_flight` TEXT,
                        `started_at` INTEGER NOT NULL,
                        `completed_at` INTEGER,
                        FOREIGN KEY(`user_id`) REFERENCES `user_profile`(`id`) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `challenges_new` (
                        `id`, `user_id`, `type`, `source`, `status`, `name`, `description`,
                        `origin_iata`, `dest_iata`, `position_iata`, `route_progress_fraction`,
                        `set_catalog_id`, `set_member_kind`, `set_total_members`, `set_visited_members`,
                        `target_distance_km`, `cumulative_distance_km`, `paused_flight`,
                        `started_at`, `completed_at`
                    )
                    SELECT
                        `id`, `user_id`, `type`, `source`, `status`, `name`, `description`,
                        `origin_iata`, `dest_iata`, `position_iata`, `route_progress_fraction`,
                        `set_catalog_id`, `set_member_kind`, `set_total_members`, `set_visited_members`,
                        `target_distance_km`, `cumulative_distance_km`, NULL,
                        `started_at`, `completed_at`
                    FROM `challenges`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `challenges`")
                db.execSQL("ALTER TABLE `challenges_new` RENAME TO `challenges`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_challenges_user_id` ON `challenges` (`user_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_challenges_user_id_status` ON `challenges` (`user_id`, `status`)")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "user_data.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
