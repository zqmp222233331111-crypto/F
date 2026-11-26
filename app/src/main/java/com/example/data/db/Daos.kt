package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memories WHERE characterId = :characterId ORDER BY updatedAt DESC")
    fun observeMemoriesForCharacter(characterId: String): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories ORDER BY updatedAt DESC")
    fun observeAllMemories(): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories WHERE characterId = :characterId ORDER BY updatedAt DESC")
    suspend fun getMemoriesForCharacter(characterId: String): List<MemoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMemory(memory: MemoryEntity)

    @Query("DELETE FROM memories WHERE characterId = :characterId AND factKey = :factKey")
    suspend fun deleteByKey(characterId: String, factKey: String): Int

    @Query("DELETE FROM memories WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM memories WHERE characterId = :characterId")
    suspend fun deleteAllForCharacter(characterId: String)

    @Query("DELETE FROM memories")
    suspend fun deleteAll()
}

@Dao
interface CustomCharacterDao {
    @Query("SELECT * FROM custom_characters ORDER BY createdAt DESC")
    fun observeAllCharacters(): Flow<List<CustomCharacterEntity>>

    @Query("SELECT * FROM custom_characters WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): CustomCharacterEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(character: CustomCharacterEntity)

    @Query("DELETE FROM custom_characters WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM custom_characters")
    suspend fun deleteAll()
}

@Dao
interface CallHistoryDao {
    @Query("SELECT * FROM call_history ORDER BY timestamp DESC")
    fun observeAllHistory(): Flow<List<CallHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: CallHistoryEntity): Long

    @Query("DELETE FROM call_history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM call_history")
    suspend fun deleteAll()
}

@Dao
interface BlockedAppDao {
    @Query("SELECT * FROM blocked_apps ORDER BY displayName ASC")
    fun observeAll(): Flow<List<BlockedAppEntity>>

    @Query("SELECT * FROM blocked_apps")
    suspend fun getAll(): List<BlockedAppEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(apps: List<BlockedAppEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(app: BlockedAppEntity)

    @Query("DELETE FROM blocked_apps WHERE packageName = :packageName")
    suspend fun delete(packageName: String)

    @Query("DELETE FROM blocked_apps")
    suspend fun deleteAll()
}
