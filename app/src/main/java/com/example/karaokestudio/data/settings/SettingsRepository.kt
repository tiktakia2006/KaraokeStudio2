package com.example.karaokestudio.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "karaoke_settings")

data class AppSettings(
    val nickname: String = "Співак",
    val countdownSeconds: Int = 3,
    val lyricsSizeSp: Int = 26,
    val micSensitivity: Int = 50,
    val musicVolume: Int = 100,
    val saveRecordings: Boolean = true,
    val keepScreenOn: Boolean = true
) {
    fun sanitized(): AppSettings = copy(
        nickname = nickname.trim().take(24).ifBlank { "Співак" },
        countdownSeconds = countdownSeconds.coerceIn(0, 10),
        lyricsSizeSp = lyricsSizeSp.coerceIn(18, 36),
        micSensitivity = micSensitivity.coerceIn(0, 100),
        musicVolume = musicVolume.coerceIn(0, 100)
    )
}

class SettingsRepository(context: Context) {

    private val store = context.applicationContext.settingsStore

    val settings: Flow<AppSettings> = store.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { read(it) }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        store.edit { prefs ->
            val next = transform(read(prefs)).sanitized()
            prefs[NICKNAME] = next.nickname
            prefs[COUNTDOWN] = next.countdownSeconds
            prefs[LYRICS_SIZE] = next.lyricsSizeSp
            prefs[SENSITIVITY] = next.micSensitivity
            prefs[MUSIC_VOLUME] = next.musicVolume
            prefs[SAVE_RECORDINGS] = next.saveRecordings
            prefs[KEEP_ON] = next.keepScreenOn
        }
    }

    private fun read(prefs: Preferences): AppSettings {
        val d = AppSettings()
        return AppSettings(
            nickname = prefs[NICKNAME] ?: d.nickname,
            countdownSeconds = prefs[COUNTDOWN] ?: d.countdownSeconds,
            lyricsSizeSp = prefs[LYRICS_SIZE] ?: d.lyricsSizeSp,
            micSensitivity = prefs[SENSITIVITY] ?: d.micSensitivity,
            musicVolume = prefs[MUSIC_VOLUME] ?: d.musicVolume,
            saveRecordings = prefs[SAVE_RECORDINGS] ?: d.saveRecordings,
            keepScreenOn = prefs[KEEP_ON] ?: d.keepScreenOn
        ).sanitized()
    }

    private companion object {
        val NICKNAME = stringPreferencesKey("nickname")
        val COUNTDOWN = intPreferencesKey("countdown_seconds")
        val LYRICS_SIZE = intPreferencesKey("lyrics_size_sp")
        val SENSITIVITY = intPreferencesKey("mic_sensitivity")
        val MUSIC_VOLUME = intPreferencesKey("music_volume")
        val SAVE_RECORDINGS = booleanPreferencesKey("save_recordings")
        val KEEP_ON = booleanPreferencesKey("keep_screen_on")
    }
}
