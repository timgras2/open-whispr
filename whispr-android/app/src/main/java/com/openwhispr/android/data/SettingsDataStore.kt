package com.openwhispr.android.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "whispr_settings")

object SettingsKeys {
    val GROQ_API_KEY = stringPreferencesKey("groq_api_key")
    val WHISPER_MODEL = stringPreferencesKey("whisper_model")
}

class SettingsRepository(private val context: Context) {

    val groqApiKey: Flow<String> =
        context.settingsDataStore.data.map { it[SettingsKeys.GROQ_API_KEY] ?: "" }

    val whisperModel: Flow<String> =
        context.settingsDataStore.data.map { it[SettingsKeys.WHISPER_MODEL] ?: DEFAULT_MODEL }

    suspend fun setGroqApiKey(key: String) {
        context.settingsDataStore.edit { it[SettingsKeys.GROQ_API_KEY] = key.trim() }
    }

    suspend fun setWhisperModel(model: String) {
        context.settingsDataStore.edit { it[SettingsKeys.WHISPER_MODEL] = model }
    }

    companion object {
        const val DEFAULT_MODEL = "whisper-large-v3-turbo"

        val AVAILABLE_MODELS = listOf(
            "whisper-large-v3-turbo" to "Large V3 Turbo (fastest)",
            "whisper-large-v3" to "Large V3 (most accurate)",
        )
    }
}
