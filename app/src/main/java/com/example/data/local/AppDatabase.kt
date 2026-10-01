package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ProfileEntity::class,
        WatchlistEntity::class,
        WatchProgressEntity::class,
        DownloadEntity::class,
        RatingEntity::class,
        ReminderEntity::class
    ],
    version = 9,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun netflixDao(): NetflixDao

    companion object {
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE user_profiles ADD COLUMN favoriteGenresJson TEXT NOT NULL DEFAULT '[]'")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE user_profiles ADD COLUMN audioLanguage TEXT NOT NULL DEFAULT 'Original'")
                db.execSQL("ALTER TABLE user_profiles ADD COLUMN subtitleLanguage TEXT NOT NULL DEFAULT 'Off'")
                db.execSQL("ALTER TABLE downloads ADD COLUMN isForYou INTEGER NOT NULL DEFAULT 0")
            }
        }
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "netflix_database.db"
                ).addMigrations(MIGRATION_7_8, MIGRATION_8_9).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
