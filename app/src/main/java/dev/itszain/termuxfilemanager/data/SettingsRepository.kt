package dev.itszain.termuxfilemanager.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dev.itszain.termuxfilemanager.AiSecureStore
import kotlinx.coroutines.flow.map

private val Context.settingsStore by preferencesDataStore("tfm-preferences")
data class AppSettings(val theme: String = "system", val language: String = "system", val grid: Boolean = false,
    val hidden: Boolean = true, val sort: String = "name", val descending: Boolean = false)

class SettingsRepository(private val context: Context) {
    private val store = context.settingsStore
    val appearance = store.data.map { p -> AppSettings(p[stringPreferencesKey("theme")] ?: "system",
        p[stringPreferencesKey("language")] ?: "system", p[booleanPreferencesKey("grid")] ?: false,
        p[booleanPreferencesKey("hidden")] ?: true, p[stringPreferencesKey("sort")] ?: "name", p[booleanPreferencesKey("descending")] ?: false) }
    suspend fun initialize() {
        store.edit { p ->
            if (p[booleanPreferencesKey("legacyImported")] != true) {
                copyLegacy(p)
                p[booleanPreferencesKey("legacyImported")] = true
            }
        }
    }
    private fun copyLegacy(p: MutablePreferences) {
        for (name in listOf("tfm_settings", AiSecureStore.SETTINGS)) {
            context.getSharedPreferences(name, Context.MODE_PRIVATE).all.forEach { (key, value) ->
                when (value) {
                    is String -> p[stringPreferencesKey(key)] = value
                    is Boolean -> p[booleanPreferencesKey(key)] = value
                    is Int -> p[intPreferencesKey(key)] = value
                    is Long -> p[longPreferencesKey(key)] = value
                }
            }
        }
    }
    // The legacy provider codecs/workspace format still read their original preferences.
    // Keep a compatibility mirror while DataStore owns the Compose settings stream.
    suspend fun set(key: String, value: String) {
        store.edit { it[stringPreferencesKey(key)] = value }
        val name = if (key == "theme") "tfm_settings" else AiSecureStore.SETTINGS
        context.getSharedPreferences(name, 0).edit().putString(key, value).commit()
    }
    suspend fun set(key: String, value: Boolean) {
        store.edit { it[booleanPreferencesKey(key)] = value }
        AiSecureStore.settings(context).edit().putBoolean(key, value).commit()
    }
    suspend fun set(key: String, value: Int) {
        store.edit { it[intPreferencesKey(key)] = value }
        AiSecureStore.settings(context).edit().putInt(key, value).commit()
    }
    suspend fun refreshWorkspace() { store.edit { copyLegacy(it) } }
}
