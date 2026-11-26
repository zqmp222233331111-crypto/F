package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        MemoryEntity::class,
        CustomCharacterEntity::class,
        CallHistoryEntity::class,
        BlockedAppEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AyeshaDatabase : RoomDatabase() {
    abstract fun memoryDao(): MemoryDao
    abstract fun customCharacterDao(): CustomCharacterDao
    abstract fun callHistoryDao(): CallHistoryDao
    abstract fun blockedAppDao(): BlockedAppDao

    companion object {
        @Volatile
        private var INSTANCE: AyeshaDatabase? = null

        fun getInstance(context: Context): AyeshaDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AyeshaDatabase::class.java,
                    "ayesha_local.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
