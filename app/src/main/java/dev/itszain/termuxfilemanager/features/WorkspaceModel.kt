package dev.itszain.termuxfilemanager.features

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import dev.itszain.termuxfilemanager.*
import dev.itszain.termuxfilemanager.core.*
import dev.itszain.termuxfilemanager.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class WorkspaceModel(val graph: AppGraph) : ViewModel() {
    val sessions = graph.dao.sessions().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val chats = graph.dao.chats().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val settings = graph.settings.appearance.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    val snippets = graph.settings.snippets.stateIn(viewModelScope, SharingStarted.Eagerly, SettingsRepository.defaultSnippets())
    val sessionId = MutableStateFlow(AiSecureStore.settings(graph.context).getString("active_terminal_session", "default").orEmpty())
    val chatId = MutableStateFlow(AiSecureStore.settings(graph.context).getString("active_ai_chat", "").orEmpty())
    val suggestedPrompt = MutableStateFlow<String?>(null)
    val error = MutableStateFlow<String?>(null)
    val commands = sessionId.flatMapLatest { graph.dao.commands(it) }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val messages = chatId.flatMapLatest { graph.dao.messages(it) }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val toolsOutput = graph.dao.commands("tools").stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        viewModelScope.launch {
            sessions.collect { if (it.isNotEmpty() && it.none { s -> s.id == sessionId.value }) selectSession(it.first().id) }
        }
        viewModelScope.launch {
            chats.collect { if (it.isNotEmpty() && it.none { c -> c.id == chatId.value }) selectChat(it.first().id) }
        }
    }

    fun selectSession(id: String) {
        sessionId.value = id
        viewModelScope.launch { graph.settings.set("active_terminal_session", id) }
    }

    fun selectChat(id: String) {
        chatId.value = id
        viewModelScope.launch { graph.settings.set("active_ai_chat", id) }
    }

    fun newSession(name: String, path: String) {
        viewModelScope.launch {
            val id = UUID.randomUUID().toString()
            graph.dao.saveSession(SessionRecord(id, name.ifBlank { "Termux" }, path.ifBlank { "~/" }))
            selectSession(id)
        }
    }

    fun saveSession(session: SessionRecord) {
        viewModelScope.launch { graph.dao.saveSession(session) }
    }

    fun clearSessionHistory(id: String) {
        viewModelScope.launch {
            graph.dao.deleteCommands(id)
            graph.dao.session(id)?.let { graph.dao.saveSession(it.copy(output = "")) }
        }
    }

    fun deleteSession(id: String) {
        viewModelScope.launch {
            if (sessions.value.size > 1) graph.database.withTransaction {
                graph.dao.deleteCommands(id)
                graph.dao.deleteSession(id)
                graph.dao.allChats().filter { it.sessionId == id }.forEach { graph.dao.saveChat(it.copy(sessionId = "")) }
            }
        }
    }

    fun addSnippet(label: String, command: String, interactive: Boolean = false) {
        viewModelScope.launch {
            val next = snippets.value + TerminalSnippet(UUID.randomUUID().toString(), label.ifBlank { command.take(20) }, command, interactive)
            graph.settings.saveSnippets(next)
        }
    }

    fun deleteSnippet(id: String) {
        viewModelScope.launch {
            graph.settings.saveSnippets(snippets.value.filterNot { it.id == id })
        }
    }

    fun newChat(name: String) {
        viewModelScope.launch {
            val id = UUID.randomUUID().toString()
            graph.dao.saveChat(ChatRecord(id, name.ifBlank { "AI" }, AiSecureStore.getActiveProvider(graph.context), sessionId.value))
            selectChat(id)
        }
    }

    fun saveChat(chat: ChatRecord) {
        viewModelScope.launch {
            graph.dao.saveChat(chat)
            withContext(Dispatchers.IO) { AgentWorkspace.persistChatOverride(graph.context, chat.id, chat.provider, chat.sessionId, chat.autoExecute) }
        }
    }

    fun clearChat(id: String) {
        viewModelScope.launch {
            graph.agent.stopAndJoin(id)
            graph.dao.deleteMessages(id)
            graph.openCode.forgetSession(id)
        }
    }

    fun deleteChat(id: String) {
        viewModelScope.launch {
            graph.agent.stopAndJoin(id)
            graph.openCode.forgetSession(id)
            graph.dao.deleteChat(id)
        }
    }

    fun regenerateLast() {
        val id = chatId.value
        if (id.isBlank()) return
        val lastUser = messages.value.lastOrNull { it.role == "user" }?.text ?: return
        graph.agent.start(id, lastUser)
    }

    fun exportChatMarkdown(): String {
        val current = chats.value.firstOrNull { it.id == chatId.value }
        return buildString {
            append("# ").append(current?.name ?: "TFM Conversation").append("\n\n")
            messages.value.forEach { msg ->
                if (msg.role in setOf("user", "assistant")) {
                    append("## ").append(if (msg.role == "user") "User" else "Assistant").append("\n\n")
                    append(msg.text.trim()).append("\n\n")
                }
            }
        }
    }

    fun exportSessionLog(): String {
        val current = sessions.value.firstOrNull { it.id == sessionId.value }
        return buildString {
            append("# Session: ").append(current?.name ?: "Termux").append(" (").append(current?.workdir ?: "~/").append(")\n\n")
            commands.value.reversed().forEach { cmd ->
                append("$ ").append(cmd.command).append('\n')
                if (cmd.stdout.isNotBlank()) append(cmd.stdout.trimEnd()).append('\n')
                if (cmd.stderr.isNotBlank()) append("[stderr]\n").append(cmd.stderr.trimEnd()).append('\n')
                cmd.exitCode?.let { append("[exit ").append(it).append("]\n") }
                append('\n')
            }
        }
    }

    fun runCommand(command: String, path: String, owner: String = sessionId.value, interactive: Boolean = false, label: String = "Terminal") {
        graph.scope.launch {
            try {
                graph.commands.submit(CommandSpec(command, path, label, owner, !interactive))
            } catch (failure: Exception) {
                error.value = failure.message ?: "io"
            }
        }
    }

    fun broadcast(command: String, interactive: Boolean = false) {
        sessions.value.forEach { runCommand(command, it.workdir, it.id, interactive) }
    }

    fun send(prompt: String, imagePath: String = "") {
        if (chatId.value.isBlank()) {
            viewModelScope.launch {
                val id = UUID.randomUUID().toString()
                graph.dao.saveChat(ChatRecord(id, prompt.take(40), AiSecureStore.getActiveProvider(graph.context), sessionId.value))
                selectChat(id)
                graph.agent.start(id, prompt, imagePath)
            }
        } else {
            graph.agent.start(chatId.value, prompt, imagePath)
        }
    }

    fun broadcastPrompt(prompt: String) {
        chats.value.forEach { graph.agent.start(it.id, prompt) }
    }
}
