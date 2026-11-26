package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.ayeshaDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "ayesha_settings"
)

enum class AccentTheme(val id: String, val labelBn: String) {
    VIOLET_PINK("violet_pink", "ভায়োলেট-পিঙ্ক"),
    TEAL_AURORA("teal_aurora", "টিল অরোরা"),
    AMBER_GLOW("amber_glow", "অ্যাম্বার গোল্ড"),
    ROSE_BERRY("rose_berry", "রোজ বেরি");

    companion object {
        fun fromId(id: String?): AccentTheme =
            entries.firstOrNull { it.id == id } ?: VIOLET_PINK
    }
}

data class UserSettings(
    val age18Confirmed: Boolean = false,
    val welcomeCompleted: Boolean = false,
    val permissionsStepCompleted: Boolean = false,
    val apiKeyVerified: Boolean = false,
    val selectedModeId: String = "assistant",
    val selectedCustomCharacterId: String? = null,
    val selectedVoice: String = "Sulafat",
    val preferredLanguage: String = "বাংলাদেশি বাংলা",
    val emotionLevel: Float = 0.7f,
    val relationshipNoticeShown: Boolean = false,
    val memoryEnabled: Boolean = true,
    val accentTheme: AccentTheme = AccentTheme.VIOLET_PINK,
    val dailyGreetingEnabled: Boolean = false,
    // Phone control settings
    val phoneControlMasterEnabled: Boolean = false,
    val accessibilityDisclosureAccepted: Boolean = false,
    val allowAppOpening: Boolean = true,
    val allowTapping: Boolean = true,
    val allowTyping: Boolean = true,
    val allowKeyboardSwitch: Boolean = true,
    val requireActionConfirmation: Boolean = true
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val AGE_18_CONFIRMED = booleanPreferencesKey("age_18_confirmed")
        val WELCOME_COMPLETED = booleanPreferencesKey("welcome_completed")
        val PERMISSIONS_COMPLETED = booleanPreferencesKey("permissions_completed")
        val API_KEY_VERIFIED = booleanPreferencesKey("api_key_verified")
        val SELECTED_MODE_ID = stringPreferencesKey("selected_mode_id")
        val SELECTED_CUSTOM_CHAR_ID = stringPreferencesKey("selected_custom_char_id")
        val SELECTED_VOICE = stringPreferencesKey("selected_voice")
        val PREFERRED_LANGUAGE = stringPreferencesKey("preferred_language")
        val EMOTION_LEVEL = floatPreferencesKey("emotion_level")
        val RELATIONSHIP_NOTICE_SHOWN = booleanPreferencesKey("relationship_notice_shown")
        val MEMORY_ENABLED = booleanPreferencesKey("memory_enabled")
        val ACCENT_THEME = stringPreferencesKey("accent_theme")
        val DAILY_GREETING_ENABLED = booleanPreferencesKey("daily_greeting_enabled")

        val PHONE_CONTROL_MASTER = booleanPreferencesKey("phone_control_master")
        val ACCESSIBILITY_DISCLOSURE_ACCEPTED = booleanPreferencesKey("accessibility_disclosure_accepted")
        val ALLOW_APP_OPENING = booleanPreferencesKey("allow_app_opening")
        val ALLOW_TAPPING = booleanPreferencesKey("allow_tapping")
        val ALLOW_TYPING = booleanPreferencesKey("allow_typing")
        val ALLOW_KEYBOARD_SWITCH = booleanPreferencesKey("allow_keyboard_switch")
        val REQUIRE_ACTION_CONFIRMATION = booleanPreferencesKey("require_action_confirmation")
    }

    val settings: Flow<UserSettings> = context.ayeshaDataStore.data.map { prefs ->
        UserSettings(
            age18Confirmed = prefs[Keys.AGE_18_CONFIRMED] ?: false,
            welcomeCompleted = prefs[Keys.WELCOME_COMPLETED] ?: false,
            permissionsStepCompleted = prefs[Keys.PERMISSIONS_COMPLETED] ?: false,
            apiKeyVerified = prefs[Keys.API_KEY_VERIFIED] ?: false,
            selectedModeId = prefs[Keys.SELECTED_MODE_ID] ?: "assistant",
            selectedCustomCharacterId = prefs[Keys.SELECTED_CUSTOM_CHAR_ID],
            selectedVoice = prefs[Keys.SELECTED_VOICE] ?: "Sulafat",
            preferredLanguage = prefs[Keys.PREFERRED_LANGUAGE] ?: "বাংলাদেশি বাংলা",
            emotionLevel = prefs[Keys.EMOTION_LEVEL] ?: 0.7f,
            relationshipNoticeShown = prefs[Keys.RELATIONSHIP_NOTICE_SHOWN] ?: false,
            memoryEnabled = prefs[Keys.MEMORY_ENABLED] ?: true,
            accentTheme = AccentTheme.fromId(prefs[Keys.ACCENT_THEME]),
            dailyGreetingEnabled = prefs[Keys.DAILY_GREETING_ENABLED] ?: false,
            phoneControlMasterEnabled = prefs[Keys.PHONE_CONTROL_MASTER] ?: false,
            accessibilityDisclosureAccepted = prefs[Keys.ACCESSIBILITY_DISCLOSURE_ACCEPTED] ?: false,
            allowAppOpening = prefs[Keys.ALLOW_APP_OPENING] ?: true,
            allowTapping = prefs[Keys.ALLOW_TAPPING] ?: true,
            allowTyping = prefs[Keys.ALLOW_TYPING] ?: true,
            allowKeyboardSwitch = prefs[Keys.ALLOW_KEYBOARD_SWITCH] ?: true,
            requireActionConfirmation = prefs[Keys.REQUIRE_ACTION_CONFIRMATION] ?: true
        )
    }

    suspend fun setWelcomeCompleted(age18Confirmed: Boolean) {
        context.ayeshaDataStore.edit { prefs ->
            prefs[Keys.AGE_18_CONFIRMED] = age18Confirmed
            prefs[Keys.WELCOME_COMPLETED] = true
        }
    }

    suspend fun setPermissionsStepCompleted(completed: Boolean) {
        context.ayeshaDataStore.edit { it[Keys.PERMISSIONS_COMPLETED] = completed }
    }

    suspend fun setApiKeyVerified(verified: Boolean) {
        context.ayeshaDataStore.edit { it[Keys.API_KEY_VERIFIED] = verified }
    }

    suspend fun setSelectedMode(modeId: String, customCharacterId: String? = null) {
        context.ayeshaDataStore.edit { prefs ->
            prefs[Keys.SELECTED_MODE_ID] = modeId
            if (customCharacterId != null) {
                prefs[Keys.SELECTED_CUSTOM_CHAR_ID] = customCharacterId
            }
        }
    }

    suspend fun setSelectedVoice(voice: String) {
        context.ayeshaDataStore.edit { it[Keys.SELECTED_VOICE] = voice }
    }

    suspend fun setPreferredLanguage(language: String) {
        context.ayeshaDataStore.edit { it[Keys.PREFERRED_LANGUAGE] = language }
    }

    suspend fun setEmotionLevel(level: Float) {
        context.ayeshaDataStore.edit { it[Keys.EMOTION_LEVEL] = level.coerceIn(0f, 1f) }
    }

    suspend fun markRelationshipNoticeShown() {
        context.ayeshaDataStore.edit { it[Keys.RELATIONSHIP_NOTICE_SHOWN] = true }
    }

    suspend fun setMemoryEnabled(enabled: Boolean) {
        context.ayeshaDataStore.edit { it[Keys.MEMORY_ENABLED] = enabled }
    }

    suspend fun setAccentTheme(theme: AccentTheme) {
        context.ayeshaDataStore.edit { it[Keys.ACCENT_THEME] = theme.id }
    }

    suspend fun setDailyGreetingEnabled(enabled: Boolean) {
        context.ayeshaDataStore.edit { it[Keys.DAILY_GREETING_ENABLED] = enabled }
    }

    suspend fun setPhoneControlMaster(enabled: Boolean) {
        context.ayeshaDataStore.edit { it[Keys.PHONE_CONTROL_MASTER] = enabled }
    }

    suspend fun setAccessibilityDisclosureAccepted(accepted: Boolean) {
        context.ayeshaDataStore.edit { it[Keys.ACCESSIBILITY_DISCLOSURE_ACCEPTED] = accepted }
    }

    suspend fun setAllowAppOpening(allowed: Boolean) {
        context.ayeshaDataStore.edit { it[Keys.ALLOW_APP_OPENING] = allowed }
    }

    suspend fun setAllowTapping(allowed: Boolean) {
        context.ayeshaDataStore.edit { it[Keys.ALLOW_TAPPING] = allowed }
    }

    suspend fun setAllowTyping(allowed: Boolean) {
        context.ayeshaDataStore.edit { it[Keys.ALLOW_TYPING] = allowed }
    }

    suspend fun setAllowKeyboardSwitch(allowed: Boolean) {
        context.ayeshaDataStore.edit { it[Keys.ALLOW_KEYBOARD_SWITCH] = allowed }
    }

    suspend fun setRequireActionConfirmation(required: Boolean) {
        context.ayeshaDataStore.edit { it[Keys.REQUIRE_ACTION_CONFIRMATION] = required }
    }

    suspend fun resetAll() {
        context.ayeshaDataStore.edit { it.clear() }
    }
}
