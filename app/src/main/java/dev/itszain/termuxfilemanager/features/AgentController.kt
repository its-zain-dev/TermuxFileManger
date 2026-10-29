package dev.itszain.termuxfilemanager.features

import dev.itszain.termuxfilemanager.*
import dev.itszain.termuxfilemanager.core.*
import dev.itszain.termuxfilemanager.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class ToolApproval(val id: String, val action: AgentAction)

class AgentController(private val graph: AppGraph, private val ai: AiGateway = graph.ai) {
    private val jobs = ConcurrentHashMap<String, Job>()
    private val approvals = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()
    private val executor = AgentToolExecutor(graph)
    val active = MutableStateFlow<Set<String>>(emptySet())
    val pending = MutableStateFlow<Map<String, ToolApproval>>(emptyMap())
    fun start(chatId: String, prompt: String, image: String = "") {
        val job = graph.scope.launch(start = CoroutineStart.LAZY) {
            active.update { it + chatId }
            try { run(chatId, prompt, image) }
            catch (error: CancellationException) { withContext(NonCancellable) { message(chatId, "status", "agent_stopped") }; throw error }
            catch (error: Exception) { message(chatId, "error", error.message.orEmpty()) }
            finally { jobs.remove(chatId); approvals.remove(chatId); active.update { it - chatId }; pending.update { it - chatId } }
        }
        if(jobs.putIfAbsent(chatId, job) == null) job.start() else job.cancel()
    }
    fun stop(chatId: String) { jobs[chatId]?.cancel() }
    suspend fun stopAndJoin(chatId: String) { jobs[chatId]?.cancelAndJoin() }
    suspend fun stopAll() { val running = jobs.values.toList(); running.forEach { it.cancel() }; running.joinAll() }
    fun approve(chatId: String, allow: Boolean, requestId: String? = null) {
        val waiting = pending.value[chatId] ?: return
        if(requestId == null || requestId == waiting.id) approvals[chatId]?.complete(allow)
    }
    private suspend fun message(chat: String, role: String, text: String, metadata: String = "{}") {
        graph.dao.saveMessage(MessageRecord(UUID.randomUUID().toString(), chat, role, text, metadata = metadata))
    }
    private suspend fun run(chatId: String, prompt: String, image: String) {
        var chat = graph.dao.chat(chatId) ?: return
        val expanded = AgentSkillManager.expandSlashCommand(graph.context, prompt) ?: error("unknown_skill")
        message(chatId, "user", prompt, JSONObject().put("expanded_prompt", expanded).toString())
        // Synchronization can change the workspace, so it is not run merely for greetings or model identity questions.
        val failures = mutableSetOf<String>()
        var invalid = 0
        for(step in 0 until 24) {
            currentCoroutineContext().ensureActive()
            chat = graph.dao.chat(chatId) ?: return
            val preferences = AiSecureStore.settings(graph.context)
            if(step > 0) delay(preferences.getInt("rate_gap_seconds", 3).coerceIn(0, 3600) * 1000L)
            val model = AiSecureStore.getModel(graph.context, chat.provider)
            val native = chat.provider == "chatgpt"
            val account = if(native) graph.chatGpt.active.value else ""
            val enabled = AgentTools.all.filter { AgentTools.enabled(graph.context, it.name) }.map { it.name }.toSet()
            val rows = graph.dao.history(chatId).filter { it.status != "invalid" }.takeLast(64)
            val history = rows.filter { it.role in setOf("user", "assistant", "tool_output", "agent_control") && it.text.isNotBlank() }.map { row ->
                val text = if(row.role == "user") JSONObject(row.metadata).optString("expanded_prompt", row.text) else if(row.role == "tool_output") "[TOOL OUTPUT]\n${row.text.take(16000)}\n[END TOOL OUTPUT]" else row.text
                (if(row.role == "assistant") "assistant" else "user") to text
            }
            val facts = JSONObject().put("provider", chat.provider).put("model", model)
                .put("workdir", graph.dao.session(chat.sessionId)?.workdir ?: "~/").put("enabled_tools", JSONArray(enabled.toList()))
            val meta = JSONObject().put("request_provider", chat.provider).put("request_model", model).put("request_account", account)
            var record = MessageRecord(UUID.randomUUID().toString(), chatId, "assistant", "", status = "streaming", metadata = meta.toString())
            graph.dao.saveMessage(record)
            val calls = linkedMapOf<String, AiEvent.ToolCall>()
            var lastSave = 0L
            try {
                ai.stream(AiRequest(chat.provider, history, if(native || step == 0) image else "", if(native) enabled else emptySet(),
                    if(native) ResponsesHistory.build(rows, model, account) else null, model, "[SESSION FACTS]\n$facts")).collect { event ->
                    when(event) {
                        is AiEvent.Text -> record = record.copy(text = record.text + event.value)
                        is AiEvent.Thinking -> record = record.copy(thinking = record.thinking + event.value)
                        is AiEvent.ToolCall -> calls[event.id] = event
                        is AiEvent.ResponseOutput -> { meta.put("response_output", JSONArray(event.items.map { JSONObject(it) })); record = record.copy(metadata = meta.toString()) }
                        is AiEvent.Complete -> record = record.copy(text = event.value, status = "complete")
                    }
                    val now = android.os.SystemClock.elapsedRealtime()
                    if(now - lastSave > 80 || event is AiEvent.Complete) { graph.dao.saveMessage(record); lastSave = now }
                }
                if(calls.isNotEmpty() && !meta.has("response_output")) {
                    meta.put("response_output", JSONArray(calls.values.map { JSONObject().put("type", "function_call").put("name", it.name).put("namespace", it.namespace).put("call_id", it.id).put("arguments", it.arguments) }))
                    record = record.copy(metadata = meta.toString()); graph.dao.saveMessage(record)
                }
            } catch(error: Exception) {
                withContext(NonCancellable) { graph.dao.saveMessage(record.copy(status = "interrupted")) }; throw error
            }
            currentCoroutineContext().ensureActive()
            val legacy = if(calls.isEmpty()) AgentAction.parse(record.text) else null
            if(calls.isEmpty() && legacy == null) {
                if(record.text.contains("\"tfm_action\"") && invalid++ < 2) {
                    graph.dao.saveMessage(record.copy(status = "invalid"))
                    message(chatId, "agent_control", "Your tool request was invalid. Use a provided native function, or ONLY one valid tfm_action object for the legacy protocol. Do not print raw JSON in your answer.")
                    continue
                }
                return
            }
            if(legacy?.tool == "finish") { graph.dao.saveMessage(record.copy(text = legacy.value("message").ifBlank { legacy.value("content") })); return }
            if(legacy?.tool == "continue") { if(invalid++ >= 2) { message(chatId, "status", "agent_no_progress"); return }; continue }
            if(legacy != null) { meta.put("tool_request", true); graph.dao.saveMessage(record.copy(text = JSONObject().put("tfm_action", JSONObject(legacy.json)).toString(), metadata = meta.toString())) }
            val work = if(calls.isNotEmpty()) calls.values.toList() else listOf<AiEvent.ToolCall?>(null)
            for(call in work) {
                currentCoroutineContext().ensureActive()
                var action: AgentAction? = null
                var toolRecord = MessageRecord(UUID.randomUUID().toString(), chatId, "tool_call", "", status = "running")
                val toolMeta = JSONObject().put("tool_name", call?.name ?: legacy!!.tool).put("call_id", call?.id.orEmpty())
                var result: String
                var declined = false
                val started = android.os.SystemClock.elapsedRealtime()
                try {
                    action = if(call != null) AgentAction.fromCall(call) else legacy!!
                    toolMeta.put("tool_name", action.tool)
                    toolRecord = toolRecord.copy(text = action.json, metadata = toolMeta.toString())
                    graph.dao.saveMessage(toolRecord)
                    check(AgentTools.enabled(graph.context, action.tool)) { "tool_disabled" }
                    check(!AgentTools.isGreeting(prompt) || action.tool == "app_info") { "The user only greeted you. Reply normally; do not execute tools for this greeting." }
                    val auto = chat.autoExecute ?: preferences.getBoolean("agent_auto_execute", false)
                    if(AgentTools.needsReview(action, auto)) {
                        val decision = CompletableDeferred<Boolean>(); approvals[chatId] = decision
                        pending.update { it + (chatId to ToolApproval(toolRecord.id, action)) }
                        toolRecord = toolRecord.copy(status = "awaiting_approval"); graph.dao.saveMessage(toolRecord)
                        declined = !decision.await()
                        pending.update { it - chatId }; approvals.remove(chatId)
                        if(declined) throw IllegalStateException("action_declined")
                        toolRecord = toolRecord.copy(status = "running"); graph.dao.saveMessage(toolRecord)
                    }
                    result = executor.execute(action, chat, model)
                    val ok = runCatching { JSONObject(result).optBoolean("ok", true) }.getOrDefault(true)
                    toolRecord = toolRecord.copy(status = if(ok) "complete" else "failed")
                } catch(timeout: TimeoutCancellationException) {
                    result = JSONObject().put("ok", false).put("error", "Tool timed out waiting for a result. A dispatched Termux command may still be running; do not repeat it automatically.").toString()
                    toolRecord = toolRecord.copy(status = "failed", metadata = toolMeta.toString())
                } catch(cancelled: CancellationException) {
                    withContext(NonCancellable) { graph.dao.saveMessage(toolRecord.copy(status = "interrupted", metadata = toolMeta.toString())) }; throw cancelled
                } catch(error: Exception) {
                    result = JSONObject().put("ok", false).put("error", error.message ?: "tool_failed").toString()
                    toolRecord = toolRecord.copy(status = if(declined) "declined" else "failed", metadata = toolMeta.toString())
                }
                toolMeta.put("elapsed_ms", android.os.SystemClock.elapsedRealtime() - started).put("tool_status", toolRecord.status)
                withContext(NonCancellable) {
                    graph.dao.saveMessage(toolRecord.copy(metadata = toolMeta.toString()))
                    message(chatId, "tool_output", result, toolMeta.toString())
                }
                if(declined) { message(chatId, "status", "action_declined"); return }
                if(toolRecord.status == "failed" && !failures.add((action?.json ?: call?.arguments.orEmpty()) + result)) {
                    message(chatId, "status", "agent_no_progress"); return
                }
            }
        }
        message(chatId, "status", "agent_step_limit")
    }
}
