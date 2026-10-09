package com.example.listenlog.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Play::class,
        TagEdit::class,
        Playlist::class,
        PlaylistItem::class,
        Favorite::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun playDao(): PlayDao
    abstract fun tagEditDao(): TagEditDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun favoriteDao(): FavoriteDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // Migration from v1 to v2: Add Favorite table and position to PlaylistItem if upgrading
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `Favorite` (
                        `path` TEXT NOT NULL,
                        `addedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`path`)
                    )
                """)
                // Ensure position column exists on PlaylistItem if upgrading
                try {
                    db.execSQL("ALTER TABLE `PlaylistItem` ADD COLUMN `position` INTEGER NOT NULL DEFAULT 0")
                } catch (e: Exception) {
                    // Column already exists or table freshly created
                }
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "listenlog.db"
                )
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
