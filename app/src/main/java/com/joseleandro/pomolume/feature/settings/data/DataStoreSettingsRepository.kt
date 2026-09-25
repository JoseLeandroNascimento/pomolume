package com.joseleandro.pomolume.feature.settings.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.joseleandro.pomolume.feature.settings.domain.PomodoroSettings
import com.joseleandro.pomolume.feature.settings.domain.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class DataStoreSettingsRepository(
    private val dataStore: DataStore<Preferences>
) : SettingsRepository {
    override fun observeSettings(): Flow<PomodoroSettings> = dataStore.data.map { preferences ->
        val payload = preferences[SETTINGS] ?: return@map PomodoroSettings()
        try {
            json.decodeFromString<PomodoroSettings>(payload).validated()
        } catch (_: SerializationException) {
            PomodoroSettings()
        } catch (_: IllegalArgumentException) {
            PomodoroSettings()
        }
    }.distinctUntilChanged()

    override suspend fun updateSettings(settings: PomodoroSettings) {
        val payload = json.encodeToString(settings.validated())
        dataStore.edit { it[SETTINGS] = payload }
    }

    companion object {
        internal val SETTINGS = stringPreferencesKey("pomodoro_settings_v1")
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    }
}
