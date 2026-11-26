package com.example.data

import com.example.data.db.AyeshaDatabase
import com.example.data.db.BlockedAppEntity
import com.example.data.db.CallHistoryEntity
import com.example.data.db.CustomCharacterEntity
import com.example.data.db.MemoryEntity
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject

data class ActionLogItem(
    val action: String,
    val detail: String,
    val success: Boolean
)

class AyeshaRepository(
    private val db: AyeshaDatabase,
    private val settingsRepository: SettingsRepository,
    private val keystoreManager: KeystoreManager
) {
    val allMemories: Flow<List<MemoryEntity>> = db.memoryDao().observeAllMemories()
    val customCharacters: Flow<List<CustomCharacterEntity>> =
        db.customCharacterDao().observeAllCharacters()
    val callHistory: Flow<List<CallHistoryEntity>> = db.callHistoryDao().observeAllHistory()
    val blockedApps: Flow<List<BlockedAppEntity>> = db.blockedAppDao().observeAll()

    suspend fun ensureDefaultBlockedAppsSeeded() {
        if (db.blockedAppDao().getAll().isEmpty()) {
            val defaults = SensitiveDataFilter.defaultBlockedApps.map { (pkg, name) ->
                BlockedAppEntity(packageName = pkg, displayName = name, isDefaultBlocked = true)
            }
            db.blockedAppDao().upsertAll(defaults)
        }
    }

    suspend fun getBlockedPackagesSet(): Set<String> {
        ensureDefaultBlockedAppsSeeded()
        return db.blockedAppDao().getAll().map { it.packageName }.toSet()
    }

    suspend fun addBlockedApp(packageName: String, displayName: String) {
        val cleanPkg = packageName.trim()
        if (cleanPkg.isNotEmpty()) {
            db.blockedAppDao().upsert(
                BlockedAppEntity(
                    packageName = cleanPkg,
                    displayName = displayName.trim().ifEmpty { cleanPkg },
                    isDefaultBlocked = false
                )
            )
        }
    }

    suspend fun removeBlockedApp(packageName: String) {
        db.blockedAppDao().delete(packageName)
    }

    fun observeMemoriesForCharacter(characterId: String): Flow<List<MemoryEntity>> =
        db.memoryDao().observeMemoriesForCharacter(characterId)

    suspend fun getMemoriesForCharacter(characterId: String): List<MemoryEntity> =
        db.memoryDao().getMemoriesForCharacter(characterId)

    suspend fun saveMemory(characterId: String, factKey: String, factValue: String): Boolean {
        val cleanKey = factKey.trim()
        val cleanVal = factValue.trim()
        if (cleanKey.isEmpty() || cleanVal.isEmpty()) return false
        if (SensitiveDataFilter.isSensitiveMemoryAttempt(cleanKey, cleanVal)) {
            return false
        }
        db.memoryDao().upsertMemory(
            MemoryEntity(
                characterId = characterId,
                factKey = cleanKey,
                factValue = cleanVal,
                updatedAt = System.currentTimeMillis()
            )
        )
        return true
    }

    suspend fun updateMemory(memory: MemoryEntity, newValue: String): Boolean {
        if (SensitiveDataFilter.isSensitiveMemoryAttempt(memory.factKey, newValue)) {
            return false
        }
        db.memoryDao().upsertMemory(
            memory.copy(factValue = newValue.trim(), updatedAt = System.currentTimeMillis())
        )
        return true
    }

    suspend fun deleteMemoryByKey(characterId: String, factKey: String): Boolean {
        return db.memoryDao().deleteByKey(characterId, factKey.trim()) > 0
    }

    suspend fun deleteMemoryById(id: Long) {
        db.memoryDao().deleteById(id)
    }

    suspend fun deleteAllMemories() {
        db.memoryDao().deleteAll()
    }

    suspend fun getCustomCharacter(id: String): CustomCharacterEntity? =
        db.customCharacterDao().getById(id)

    suspend fun saveCustomCharacter(character: CustomCharacterEntity) {
        db.customCharacterDao().upsert(character)
    }

    suspend fun deleteCustomCharacter(id: String) {
        db.customCharacterDao().deleteById(id)
        db.memoryDao().deleteAllForCharacter(id)
    }

    suspend fun insertCallHistory(
        callType: String,
        durationSeconds: Long,
        modeName: String,
        characterName: String,
        actions: List<ActionLogItem>
    ) {
        val jsonArray = JSONArray()
        actions.forEach { item ->
            jsonArray.put(
                JSONObject().apply {
                    put("action", item.action)
                    put("detail", item.detail)
                    put("success", item.success)
                }
            )
        }
        db.callHistoryDao().insert(
            CallHistoryEntity(
                timestamp = System.currentTimeMillis(),
                callType = callType,
                durationSeconds = durationSeconds,
                modeName = modeName,
                characterName = characterName,
                actionsJson = jsonArray.toString()
            )
        )
    }

    suspend fun deleteCallHistory(id: Long) {
        db.callHistoryDao().deleteById(id)
    }

    suspend fun deleteAllCallHistory() {
        db.callHistoryDao().deleteAll()
    }

    suspend fun deleteAllAppData() {
        keystoreManager.clearApiKey()
        db.memoryDao().deleteAll()
        db.customCharacterDao().deleteAll()
        db.callHistoryDao().deleteAll()
        db.blockedAppDao().deleteAll()
        settingsRepository.resetAll()
    }

    companion object {
        fun parseActionsJson(actionsJson: String): List<ActionLogItem> {
            return try {
                val arr = JSONArray(actionsJson)
                buildList {
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        add(
                            ActionLogItem(
                                action = obj.optString("action"),
                                detail = obj.optString("detail"),
                                success = obj.optBoolean("success", false)
                            )
                        )
                    }
                }
            } catch (_: Exception) {
                emptyList()
            }
        }
    }
}
