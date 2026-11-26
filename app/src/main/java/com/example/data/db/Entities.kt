package com.example.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "memories",
    indices = [Index(value = ["characterId", "factKey"], unique = true)]
)
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val characterId: String,
    val factKey: String,
    val factValue: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "custom_characters")
data class CustomCharacterEntity(
    @PrimaryKey val id: String,
    val name: String,
    val relationshipRole: String,
    val callUserAs: String,
    val language: String,
    val voiceName: String,
    val interests: String,
    val description: String,
    val funLevel: Float,
    val emotionLevel: Float,
    val seriousnessLevel: Float,
    val talkativenessLevel: Float,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "call_history")
data class CallHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val callType: String, // "AUDIO" or "VIDEO"
    val durationSeconds: Long,
    val modeName: String,
    val characterName: String,
    val actionsJson: String // JSON array of {action, target, success, message}
)

@Entity(tableName = "blocked_apps")
data class BlockedAppEntity(
    @PrimaryKey val packageName: String,
    val displayName: String,
    val isDefaultBlocked: Boolean = false
)
