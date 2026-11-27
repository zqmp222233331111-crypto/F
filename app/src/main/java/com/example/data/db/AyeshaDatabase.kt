package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        MemoryEntity::class,
        CustomCharacterEntity::class,
        CallHistoryEntity::class,
        BlockedAppEntity::class,
        CreatorAuditEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AyeshaDatabase : RoomDatabase() {
    abstract fun memoryDao(): MemoryDao
    abstract fun customCharacterDao(): CustomCharacterDao
    abstract fun callHistoryDao(): CallHistoryDao
    abstract fun blockedAppDao(): BlockedAppDao
    abstract fun creatorAuditDao(): CreatorAuditDao

    companion object {
        @Volatile
        private var INSTANCE: AyeshaDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS creator_audits (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        source TEXT NOT NULL,
                        platform TEXT NOT NULL,
                        videoOrTopic TEXT NOT NULL,
                        categoryBn TEXT NOT NULL,
                        viralScore INTEGER NOT NULL,
                        viewsCount INTEGER NOT NULL,
                        ctrPercent REAL NOT NULL,
                        retentionPercent REAL NOT NULL,
                        problemSummaryBn TEXT NOT NULL,
                        actionTakenBn TEXT NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        fun getInstance(context: Context): AyeshaDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AyeshaDatabase::class.java,
                    "ayesha_local.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
