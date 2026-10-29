package dev.itszain.termuxfilemanager

import android.app.Application
import android.content.Context
import dev.itszain.termuxfilemanager.data.*
import dev.itszain.termuxfilemanager.features.AgentController
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject

class TfmApplication : Application() {
    val graph by lazy { AppGraph(this) }
    override fun onCreate() { super.onCreate(); graph.initialize() }
}
class AppGraph(val context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val database = AppDatabase.create(context)
    val dao = database.dao()
    val settings = SettingsRepository(context)
    val commands = TermuxGateway(context, dao)
    val files = TermuxFileRepository(context, commands)
    val chatGpt = ChatGptAuth(context, scope) { agent.stopAll() }
    val ai = ProviderGateway(context)
    val agent by lazy { AgentController(this) }
    val ready = MutableStateFlow(false)
    val startupError = MutableStateFlow<String?>(null)
    fun initialize() {
        scope.launch {
            try {
                settings.initialize()
                chatGpt.load()
                LegacyMigration(context, database).run()
                dao.interruptStreams()
                dao.pruneFileResults()
                if (dao.allSessions().isEmpty()) dao.saveSession(SessionRecord("default", "Termux", "~/"))
                ready.value = true
            } catch (error: Exception) { startupError.value = error.message ?: "migration_failed" }
        }
    }
    suspend fun applyWorkspace(output: String) {
        AgentWorkspace.applyRead(context, output)
        settings.refreshWorkspace()
        val config = runCatching { JSONObject(AiSecureStore.settings(context).getString("agent_workspace_config", "{}").orEmpty()) }.getOrNull() ?: return
        val overrides = config.optJSONObject("sessions") ?: return
        for (chat in dao.allChats()) {
            val entry = overrides.optJSONObject(chat.id) ?: continue
            val provider = entry.optString("provider", chat.provider).takeIf { name -> AiProvider.all().any { it.id == name } } ?: chat.provider
            dao.saveChat(chat.copy(provider = provider, sessionId = entry.optString("terminalSessionId", chat.sessionId),
                autoExecute = if(entry.has("autoExecute")) entry.optBoolean("autoExecute") else chat.autoExecute))
        }
    }
}
fun Context.graph(): AppGraph = (applicationContext as TfmApplication).graph
