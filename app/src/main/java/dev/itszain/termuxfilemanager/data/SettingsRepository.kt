package dev.itszain.termuxfilemanager.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dev.itszain.termuxfilemanager.AiSecureStore
import dev.itszain.termuxfilemanager.core.TerminalSnippet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.settingsStore by preferencesDataStore("tfm-preferences")

data class AppSettings(
    val theme: String = "system",
    val dynamicColor: Boolean = false,
    val language: String = "system",
    val grid: Boolean = false,
    val hidden: Boolean = true,
    val sort: String = "name",
    val descending: Boolean = false,
    val editorFontSize: Int = 14,
    val editorWordWrap: Boolean = false,
    val editorLineNumbers: Boolean = true,
    val editorSyntaxHighlight: Boolean = true,
    val editorTabSize: Int = 4,
    val editorAutoIndent: Boolean = true,
    val terminalConfirmRun: Boolean = true,
    val backgroundNotifications: Boolean = true
)

class SettingsRepository(private val context: Context) {
    private val store = context.settingsStore

    val appearance: Flow<AppSettings> = store.data.map { p ->
        AppSettings(
            theme = p[stringPreferencesKey("theme")] ?: "system",
            dynamicColor = p[booleanPreferencesKey("dynamic_color")] ?: false,
            language = p[stringPreferencesKey("language")] ?: "system",
            grid = p[booleanPreferencesKey("grid")] ?: false,
            hidden = p[booleanPreferencesKey("hidden")] ?: true,
            sort = p[stringPreferencesKey("sort")] ?: "name",
            descending = p[booleanPreferencesKey("descending")] ?: false,
            editorFontSize = (p[intPreferencesKey("editor_font_size")] ?: 14).coerceIn(11, 22),
            editorWordWrap = p[booleanPreferencesKey("editor_word_wrap")] ?: false,
            editorLineNumbers = p[booleanPreferencesKey("editor_line_numbers")] ?: true,
            editorSyntaxHighlight = p[booleanPreferencesKey("editor_syntax_highlight")] ?: true,
            editorTabSize = (p[intPreferencesKey("editor_tab_size")] ?: 4).coerceIn(2, 8),
            editorAutoIndent = p[booleanPreferencesKey("editor_auto_indent")] ?: true,
            terminalConfirmRun = p[booleanPreferencesKey("terminal_confirm_run")] ?: true,
            backgroundNotifications = p[booleanPreferencesKey("background_notifications")] ?: true
        )
    }

    val snippets: Flow<List<TerminalSnippet>> = store.data.map { p ->
        val raw = p[stringPreferencesKey("terminal_snippets")]
        if (raw.isNullOrBlank()) defaultSnippets() else parseSnippets(raw)
    }

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

    // Keep compatibility mirror while DataStore owns the reactive Compose stream.
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

    suspend fun saveSnippets(items: List<TerminalSnippet>) {
        val array = JSONArray()
        items.forEach { s ->
            array.put(
                JSONObject()
                    .put("id", s.id)
                    .put("label", s.label)
                    .put("command", s.command)
                    .put("interactive", s.interactive)
            )
        }
        set("terminal_snippets", array.toString())
    }

    suspend fun refreshWorkspace() {
        store.edit { copyLegacy(it) }
    }

    companion object {
        fun defaultSnippets(): List<TerminalSnippet> = listOf(
            TerminalSnippet("builtin-ls", "ls -la", "ls -la --color=always"),
            TerminalSnippet("builtin-git-st", "git status", "git status --short --branch"),
            TerminalSnippet("builtin-git-log", "git log", "git log --oneline --graph -n 15"),
            TerminalSnippet("builtin-disk", "df -h", "df -h"),
            TerminalSnippet("builtin-ps", "ps aux", "ps aux"),
            TerminalSnippet("builtin-pkg", "pkg up", "pkg update && pkg upgrade -y"),
            TerminalSnippet("builtin-shell", "bash", "bash -l", interactive = true)
        )

        private fun parseSnippets(raw: String): List<TerminalSnippet> = runCatching {
            val array = JSONArray(raw)
            List(array.length()) { idx ->
                val item = array.getJSONObject(idx)
                TerminalSnippet(
                    id = item.optString("id", "snip-$idx"),
                    label = item.optString("label", item.optString("command").take(16)),
                    command = item.optString("command"),
                    interactive = item.optBoolean("interactive", false)
                )
            }.filter { it.command.isNotBlank() }
        }.getOrElse { defaultSnippets() }
    }
}
