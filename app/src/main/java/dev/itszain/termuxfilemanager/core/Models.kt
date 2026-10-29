package dev.itszain.termuxfilemanager.core

import kotlinx.coroutines.flow.Flow
import org.json.JSONObject

data class FileEntry(val name: String, val path: String, val directory: Boolean, val link: Boolean,
    val size: Long, val modified: Long, val mode: String, val readable: Boolean = true, val linkTarget: String = "") {
    companion object {
        fun from(json: JSONObject) = FileEntry(json.getString("name"), json.getString("path"), json.optBoolean("isDir"),
            json.optBoolean("isLink"), json.optLong("size"), (json.optDouble("mtime", 0.0) * 1000).toLong(), json.optString("mode"),
            json.optBoolean("readable", true), json.optString("linkTarget"))
    }
}
data class DirectoryPage(val path: String, val entries: List<FileEntry>, val next: Int, val more: Boolean, val total: Int, val truncated: Boolean)
data class FileContent(val path: String, val text: String, val fingerprint: String)
class FileFailure(val code: String, val path: String = "") : Exception(code)
data class CommandSpec(val command: String, val workdir: String = "~/", val label: String = "TFM",
    val ownerId: String = "tools", val background: Boolean = true, val executable: String = "\$PREFIX/bin/bash",
    val arguments: List<String> = listOf("-lc", command), val stdin: String? = null, val kind: String = "shell")
data class CommandResult(val id: Long, val stdout: String, val stderr: String, val exitCode: Int) {
    val output get() = listOf(stdout, stderr).filter { it.isNotBlank() }.joinToString("\n")
}
interface CommandGateway {
    suspend fun submit(spec: CommandSpec): Long
    suspend fun execute(spec: CommandSpec): CommandResult
}
interface FileRepository {
    suspend fun list(path: String, query: String = "", hidden: Boolean = true, sort: String = "name", descending: Boolean = false, offset: Int = 0): DirectoryPage
    suspend fun read(path: String): FileContent
    suspend fun write(content: FileContent): FileContent
    suspend fun action(action: String, path: String, arguments: Map<String, String> = emptyMap()): String
    suspend fun probe(): String
}
sealed interface AiEvent {
    data class Text(val value: String) : AiEvent
    data class Thinking(val value: String) : AiEvent
    data class Complete(val value: String) : AiEvent
    data class ToolCall(val id: String, val name: String, val arguments: String, val namespace: String = "termux") : AiEvent
    data class ResponseOutput(val items: List<String>) : AiEvent
}
data class AiRequest(val provider: String, val history: List<Pair<String, String>>, val imagePath: String = "",
    val nativeTools: Set<String> = emptySet(), val responseItems: List<String>? = null, val model: String = "", val context: String = "")
interface AiGateway { fun stream(request: AiRequest): Flow<AiEvent> }
fun shellQuote(value: String) = "'" + value.replace("'", "'\\''") + "'"
