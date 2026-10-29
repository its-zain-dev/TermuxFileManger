package dev.itszain.termuxfilemanager.features

import dev.itszain.termuxfilemanager.*
import dev.itszain.termuxfilemanager.core.*
import dev.itszain.termuxfilemanager.data.ChatRecord
import org.json.JSONArray
import kotlinx.coroutines.CancellationException
import org.json.JSONObject

class AgentToolExecutor(private val graph: AppGraph) {
    suspend fun execute(action: AgentAction, chat: ChatRecord, model: String): String {
        val directory = graph.dao.session(chat.sessionId)?.workdir ?: "~/"
        val requested = action.value("path").ifBlank { directory }
        val path = if(requested.startsWith("/") || requested == "~" || requested.startsWith("~/")) requested else directory.trimEnd('/') + "/" + requested
        return when(action.tool) {
            "app_info" -> JSONObject().put("ok", true).put("provider", chat.provider).put("model", model)
                .put("identity_source", "Configured model ID sent in this request; backend weights are not independently attested.")
                .put("session", graph.dao.session(chat.sessionId)?.let { JSONObject().put("name", it.name).put("workdir", it.workdir) } ?: JSONObject.NULL)
                .put("termux_permission", TermuxCommandRunner.hasPermission(graph.context))
                .put("tools", JSONArray(AgentTools.all.filter { AgentTools.enabled(graph.context, it.name) }.map { it.name })).toString()
            "list_files", "search_files" -> {
                val page = graph.files.list(path, query = if(action.tool == "search_files") action.value("query") else "",
                    offset = action.value("offset").toIntOrNull()?.coerceAtLeast(0) ?: 0)
                JSONObject().put("ok", true).put("path", page.path).put("total", page.total).put("more", page.more || page.entries.size > 40)
                    .put("next_offset", (action.value("offset").toIntOrNull() ?: 0) + page.entries.take(40).size).put("truncated", page.truncated).put("entries", JSONArray().apply {
                        page.entries.take(40).forEach { put(JSONObject().put("name", it.name).put("path", it.path).put("directory", it.directory).put("size", it.size)) }
                    }).toString()
            }
            "read_file" -> {
                val file = graph.files.read(path)
                val json = JSONObject().put("ok", true).put("path", path).put("text", file.text).put("fingerprint", file.fingerprint)
                check(json.toString().length <= 15000) { "File exceeds assistant read limit; inspect a smaller range with a reviewed shell command." }
                json.toString()
            }
            "write_file" -> {
                require(action.value("fingerprint").isNotBlank()) { "Read the file first and provide its fingerprint." }
                val result = graph.files.write(FileContent(path, action.value("text"), action.value("fingerprint")))
                JSONObject().put("ok", true).put("path", path).put("fingerprint", result.fingerprint).toString()
            }
            "create_file" -> {
                val parent = path.substringBeforeLast('/', "~").ifBlank { "/" }
                val created = graph.files.action("create", parent, mapOf("name" to path.substringAfterLast('/')))
                val current = graph.files.read(created)
                try { graph.files.write(current.copy(text = action.value("text"))) } catch(cancelled: CancellationException) { throw cancelled } catch(error: Exception) { throw IllegalStateException("Created $created, but writing its contents failed: ${error.message}", error) }
                JSONObject().put("ok", true).put("path", created).toString()
            }
            "run_shell", "git_status", "project_inspect" -> {
                val command = when(action.tool) {
                    "git_status" -> "git status --short --branch && git diff --stat"
                    "project_inspect" -> "pwd; ls -la; for c in git python node npm java javac clang make cargo go; do command -v \"\$c\" || true; done"
                    else -> action.value("command")
                }
                val dir = if(action.tool == "run_shell") action.value("workdir").ifBlank { path } else path
                val result = graph.commands.execute(CommandSpec(command, dir, "AI · ${action.tool}", chat.sessionId.ifBlank { chat.id }))
                JSONObject().put("ok", result.exitCode == 0).put("exit_code", result.exitCode)
                    .put("stdout", result.stdout.takeLast(12000)).put("stderr", result.stderr.takeLast(3000)).toString()
            }
            "web_search" -> AgentWebSearch.search(action.value("query"))
            "save_memory" -> {
                val result = graph.commands.execute(CommandSpec(AgentWorkspace.appendMemoryCommand(action.value("content")), ownerId = chat.id, label = "Memory", kind = "workspace"))
                if(result.exitCode == 0) {
                    val old = AiSecureStore.settings(graph.context).getString("agent_workspace_memory", "").orEmpty()
                    graph.settings.set("agent_workspace_memory", old + "\n" + action.value("content"))
                }
                JSONObject().put("ok", result.exitCode == 0).put("exit_code", result.exitCode).put("output", result.output.take(12000)).toString()
            }
            "activate_skill" -> (AgentSkillManager.findInstalled(graph.context, action.value("name")) ?: error("unknown_skill")).optString("content").take(14000)
            "install_skill" -> {
                val skill = AgentSkillManager.installFromUrl(graph.context, action.value("url"), action.value("name"))
                graph.settings.refreshWorkspace()
                JSONObject().put("ok", true).put("name", skill.name).put("description", skill.description).toString()
            }
            else -> error("unknown_action")
        }
    }
}
