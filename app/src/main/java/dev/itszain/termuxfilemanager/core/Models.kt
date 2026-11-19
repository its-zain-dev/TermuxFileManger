package dev.itszain.termuxfilemanager.core

import kotlinx.coroutines.flow.Flow
import org.json.JSONObject

data class FileEntry(
    val name: String,
    val path: String,
    val directory: Boolean,
    val link: Boolean,
    val size: Long,
    val modified: Long,
    val mode: String,
    val readable: Boolean = true,
    val linkTarget: String = "",
    val writable: Boolean = true,
    val modeText: String = "",
    val ext: String = "",
    val category: String = "other"
) {
    val isImage: Boolean get() = !directory && category == "image"
    val isArchive: Boolean get() = !directory && category == "archive"
    val isCode: Boolean get() = !directory && category == "code"
    companion object {
        fun from(json: JSONObject) = FileEntry(
            name = json.getString("name"),
            path = json.getString("path"),
            directory = json.optBoolean("isDir"),
            link = json.optBoolean("isLink"),
            size = json.optLong("size"),
            modified = (json.optDouble("mtime", 0.0) * 1000).toLong(),
            mode = json.optString("mode"),
            readable = json.optBoolean("readable", true),
            linkTarget = json.optString("linkTarget"),
            writable = json.optBoolean("writable", true),
            modeText = json.optString("modeText"),
            ext = json.optString("ext"),
            category = json.optString("category", if (json.optBoolean("isDir")) "dir" else "other")
        )
    }
}

data class DirectoryPage(
    val path: String,
    val entries: List<FileEntry>,
    val next: Int,
    val more: Boolean,
    val total: Int,
    val truncated: Boolean,
    val gitBranch: String = "",
    val diskTotal: Long = 0L,
    val diskFree: Long = 0L,
    val diskUsed: Long = 0L
)

data class GrepMatch(val path: String, val name: String, val line: Int, val column: Int, val preview: String)
data class GrepResult(val path: String, val matches: List<GrepMatch>, val total: Int, val filesSearched: Int, val truncated: Boolean)
data class FileContent(val path: String, val text: String, val fingerprint: String)
data class FileRange(val path: String, val text: String, val startLine: Int, val endLine: Int, val totalLines: Int, val size: Long, val more: Boolean)
data class ImagePreviewData(val path: String, val mime: String, val size: Long, val base64: String)
data class FileHashes(val path: String, val size: Long, val md5: String, val sha1: String, val sha256: String)
data class DiskUsageItem(val name: String, val path: String, val size: Long, val directory: Boolean)
data class DiskUsageReport(val path: String, val totalBytes: Long, val fileCount: Int, val dirCount: Int, val linkCount: Int, val truncated: Boolean, val largest: List<DiskUsageItem>)
data class ArchiveEntryInfo(val name: String, val size: Long, val directory: Boolean)
data class ArchiveListing(val path: String, val entries: List<ArchiveEntryInfo>, val total: Int, val totalSize: Long, val truncated: Boolean)
data class PatchResult(val path: String, val fingerprint: String, val replacements: Int, val diff: String)
data class TerminalSnippet(val id: String, val label: String, val command: String, val interactive: Boolean = false)

class FileFailure(val code: String, val path: String = "") : Exception(code)

data class CommandSpec(
    val command: String,
    val workdir: String = "~/",
    val label: String = "TFM",
    val ownerId: String = "tools",
    val background: Boolean = true,
    val executable: String = "\$PREFIX/bin/bash",
    val arguments: List<String> = listOf("-lc", command),
    val stdin: String? = null,
    val kind: String = "shell"
)

data class CommandResult(val id: Long, val stdout: String, val stderr: String, val exitCode: Int) {
    val output get() = listOf(stdout, stderr).filter { it.isNotBlank() }.joinToString("\n")
}

interface CommandGateway {
    suspend fun submit(spec: CommandSpec): Long
    suspend fun execute(spec: CommandSpec, timeoutMs: Long = 120_000): CommandResult
}

interface FileRepository {
    suspend fun list(
        path: String,
        query: String = "",
        hidden: Boolean = true,
        sort: String = "name",
        descending: Boolean = false,
        offset: Int = 0,
        filter: String = "all"
    ): DirectoryPage
    suspend fun read(path: String): FileContent
    suspend fun write(content: FileContent, force: Boolean = false): FileContent
    suspend fun action(action: String, path: String, arguments: Map<String, String> = emptyMap()): String
    suspend fun probe(): String
    suspend fun grep(path: String, query: String, hidden: Boolean = false, caseSensitive: Boolean = false, regex: Boolean = false): GrepResult =
        throw UnsupportedOperationException("grep")
    suspend fun readRange(path: String, startLine: Int = 1, maxLines: Int = 200): FileRange =
        throw UnsupportedOperationException("read_range")
    suspend fun readBase64(path: String): ImagePreviewData =
        throw UnsupportedOperationException("read_base64")
    suspend fun hash(path: String): FileHashes =
        throw UnsupportedOperationException("hash")
    suspend fun diskUsage(path: String): DiskUsageReport =
        throw UnsupportedOperationException("disk_usage")
    suspend fun archiveList(path: String): ArchiveListing =
        throw UnsupportedOperationException("archive_list")
    suspend fun archiveCreate(sources: List<String>, destDir: String, name: String, format: String = "zip"): String =
        throw UnsupportedOperationException("archive_create")
    suspend fun archiveExtract(path: String, destDir: String, overwrite: Boolean = false): Int =
        throw UnsupportedOperationException("archive_extract")
    suspend fun patch(path: String, oldText: String, newText: String, fingerprint: String, replaceAll: Boolean = false): PatchResult =
        throw UnsupportedOperationException("patch")
}

sealed interface AiEvent {
    data class Text(val value: String) : AiEvent
    data class Thinking(val value: String) : AiEvent
    data class Complete(val value: String) : AiEvent
    data class ToolCall(val id: String, val name: String, val arguments: String, val namespace: String = "termux") : AiEvent
    data class ResponseOutput(val items: List<String>) : AiEvent
}

data class AiRequest(
    val provider: String,
    val history: List<Pair<String, String>>,
    val imagePath: String = "",
    val nativeTools: Set<String> = emptySet(),
    val responseItems: List<String>? = null,
    val model: String = "",
    val context: String = "",
    val sessionKey: String = "",
    val sessionTitle: String = "",
    val workdir: String = "",
    val autoApprove: Boolean = false
)

interface AiGateway {
    fun stream(request: AiRequest): Flow<AiEvent>
}

fun shellQuote(value: String) = "'" + value.replace("'", "'\\''") + "'"
