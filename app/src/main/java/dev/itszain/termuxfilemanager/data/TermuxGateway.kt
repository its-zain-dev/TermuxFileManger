package dev.itszain.termuxfilemanager.data

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import dev.itszain.termuxfilemanager.CommandResultService
import dev.itszain.termuxfilemanager.TermuxCommandRunner
import dev.itszain.termuxfilemanager.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject

class TermuxGateway(private val context: Context, private val dao: AppDao) : CommandGateway {
    override suspend fun submit(spec: CommandSpec): Long = withContext(Dispatchers.IO) {
        check(TermuxCommandRunner.hasPermission(context)) { "termux_permission" }
        require(spec.command.isNotBlank() || spec.kind == "files") { "empty_command" }
        val id = dao.insertCommand(
            CommandRecord(
                ownerId = spec.ownerId,
                label = spec.label,
                command = spec.command,
                workdir = spec.workdir,
                kind = spec.kind
            )
        )
        try {
            check(id <= Int.MAX_VALUE) { "request_id_exhausted" }
            val callback = Intent(context, CommandResultService::class.java)
                .putExtra(CommandResultService.EXTRA_REQUEST_ID, id.toInt())
            val flags = PendingIntent.FLAG_ONE_SHOT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val pending = PendingIntent.getService(context, id.toInt(), callback, flags)
            val intent = Intent("com.termux.RUN_COMMAND").setClassName("com.termux", "com.termux.app.RunCommandService")
                .putExtra("com.termux.RUN_COMMAND_PATH", spec.executable)
                .putExtra("com.termux.RUN_COMMAND_ARGUMENTS", spec.arguments.toTypedArray())
                .putExtra("com.termux.RUN_COMMAND_WORKDIR", spec.workdir.ifBlank { "~/" })
                .putExtra("com.termux.RUN_COMMAND_BACKGROUND", spec.background)
                .putExtra("com.termux.RUN_COMMAND_SESSION_ACTION", "0")
                .putExtra("com.termux.RUN_COMMAND_COMMAND_LABEL", spec.label)
                .putExtra("com.termux.RUN_COMMAND_PENDING_INTENT", pending)
            spec.stdin?.let { intent.putExtra("com.termux.RUN_COMMAND_STDIN", it) }
            context.startService(intent)
        } catch (error: Exception) {
            dao.completeCommand(id, "failed", "", error.message.orEmpty(), -1, System.currentTimeMillis())
            throw error
        }
        id
    }

    override suspend fun execute(spec: CommandSpec, timeoutMs: Long): CommandResult {
        val id = submit(spec)
        // A timeout never changes a command to 'cancelled': Termux may still be executing it.
        val result = withTimeout(timeoutMs) { dao.observeCommand(id).filterNotNull().first { it.completed != null } }
        return CommandResult(id, result.stdout, result.stderr, result.exitCode ?: -1)
    }

    /** Removes a command row after its output was consumed, for internal credentials and pairing links. */
    suspend fun deleteCommand(id: Long) = dao.deleteCommand(id)

    suspend fun accept(intent: Intent) {
        val id = intent.getIntExtra(CommandResultService.EXTRA_REQUEST_ID, -1).toLong()
        if (id < 0 || dao.command(id) == null) return
        val result = intent.getBundleExtra("result")
        val exit = result?.getInt("exitCode", -1) ?: -1
        val error = result?.getInt("err", -1) ?: 1
        val stdout = result?.getString("stdout").orEmpty()
        val stderr = listOf(result?.getString("stderr").orEmpty(), result?.getString("errmsg").orEmpty())
            .filter { it.isNotBlank() }
            .joinToString("\n")
        dao.completeCommand(
            id,
            if (exit == 0 && error == -1) "complete" else "failed",
            stdout,
            if (result == null) "missing_result" else stderr,
            if (error != -1) -1 else exit,
            System.currentTimeMillis()
        )
    }
}

class TermuxFileRepository(private val context: Context, private val gateway: CommandGateway) : FileRepository {
    private val script by lazy { context.assets.open("file_bridge.py").bufferedReader().use { it.readText() } }

    private suspend fun request(action: String, payload: JSONObject, timeoutMs: Long = 120_000): JSONObject = withContext(Dispatchers.IO) {
        payload.put("version", 1)
        val result = gateway.execute(
            CommandSpec(
                command = "$action ${payload.optString("path")}",
                ownerId = "files",
                label = action,
                kind = "files",
                executable = "\$PREFIX/bin/python",
                arguments = listOf("-c", script, action),
                stdin = payload.toString()
            ),
            timeoutMs = timeoutMs
        )
        if (result.exitCode != 0) throw IllegalStateException(result.stderr.ifBlank { "bridge_unavailable" })
        val root = try {
            JSONObject(result.stdout)
        } catch (_: Exception) {
            throw FileFailure("invalid_response")
        }
        if (!root.optBoolean("ok")) throw FileFailure(root.optString("code", "io"), root.optString("path"))
        root
    }

    override suspend fun list(
        path: String,
        query: String,
        hidden: Boolean,
        sort: String,
        descending: Boolean,
        offset: Int,
        filter: String
    ): DirectoryPage {
        val root = request(
            if (query.isBlank()) "list" else "search",
            JSONObject()
                .put("path", path)
                .put("query", query)
                .put("showHidden", hidden)
                .put("sort", sort)
                .put("descending", descending)
                .put("offset", offset)
                .put("limit", 100)
                .put("filter", filter)
        )
        val items = root.getJSONArray("items")
        val disk = root.optJSONObject("disk")
        return DirectoryPage(
            path = root.getString("path"),
            entries = List(items.length()) { FileEntry.from(items.getJSONObject(it)) },
            next = root.getInt("nextOffset"),
            more = root.getBoolean("more"),
            total = root.getInt("total"),
            truncated = root.optBoolean("truncated"),
            gitBranch = root.optString("gitBranch"),
            diskTotal = disk?.optLong("total", 0L) ?: 0L,
            diskFree = disk?.optLong("free", 0L) ?: 0L,
            diskUsed = disk?.optLong("used", 0L) ?: 0L
        )
    }

    override suspend fun grep(path: String, query: String, hidden: Boolean, caseSensitive: Boolean, regex: Boolean): GrepResult {
        val root = request(
            "grep",
            JSONObject()
                .put("path", path)
                .put("query", query)
                .put("showHidden", hidden)
                .put("caseSensitive", caseSensitive)
                .put("regex", regex)
                .put("maxMatches", 200)
        )
        val arr = root.getJSONArray("matches")
        val matches = List(arr.length()) {
            val m = arr.getJSONObject(it)
            GrepMatch(
                path = m.getString("path"),
                name = m.getString("name"),
                line = m.getInt("line"),
                column = m.optInt("column", 1),
                preview = m.optString("preview")
            )
        }
        return GrepResult(
            path = root.getString("path"),
            matches = matches,
            total = root.getInt("total"),
            filesSearched = root.optInt("filesSearched"),
            truncated = root.optBoolean("truncated")
        )
    }

    override suspend fun read(path: String): FileContent {
        val root = request("read", JSONObject().put("path", path))
        return FileContent(path, root.getString("text"), root.getString("fingerprint"))
    }

    override suspend fun readRange(path: String, startLine: Int, maxLines: Int): FileRange {
        val root = request(
            "read_range",
            JSONObject().put("path", path).put("startLine", startLine).put("maxLines", maxLines)
        )
        return FileRange(
            path = root.getString("path"),
            text = root.getString("text"),
            startLine = root.getInt("startLine"),
            endLine = root.getInt("endLine"),
            totalLines = root.getInt("totalLines"),
            size = root.getLong("size"),
            more = root.getBoolean("more")
        )
    }

    override suspend fun readBase64(path: String): ImagePreviewData {
        val root = request("read_base64", JSONObject().put("path", path))
        return ImagePreviewData(
            path = root.getString("path"),
            mime = root.getString("mime"),
            size = root.getLong("size"),
            base64 = root.getString("data")
        )
    }

    override suspend fun write(content: FileContent, force: Boolean): FileContent {
        val root = request(
            "write",
            JSONObject()
                .put("path", content.path)
                .put("text", content.text)
                .put("fingerprint", content.fingerprint)
                .put("force", force)
        )
        return content.copy(fingerprint = root.getString("fingerprint"))
    }

    override suspend fun patch(
        path: String,
        oldText: String,
        newText: String,
        fingerprint: String,
        replaceAll: Boolean
    ): PatchResult {
        val root = request(
            "patch",
            JSONObject()
                .put("path", path)
                .put("oldText", oldText)
                .put("newText", newText)
                .put("fingerprint", fingerprint)
                .put("replaceAll", replaceAll)
        )
        return PatchResult(
            path = root.getString("path"),
            fingerprint = root.getString("fingerprint"),
            replacements = root.getInt("replacements"),
            diff = root.optString("diff")
        )
    }

    override suspend fun hash(path: String): FileHashes {
        val root = request("hash", JSONObject().put("path", path))
        return FileHashes(
            path = root.getString("path"),
            size = root.getLong("size"),
            md5 = root.getString("md5"),
            sha1 = root.getString("sha1"),
            sha256 = root.getString("sha256")
        )
    }

    override suspend fun diskUsage(path: String): DiskUsageReport {
        val root = request("disk_usage", JSONObject().put("path", path), timeoutMs = 180_000)
        val arr = root.optJSONArray("largest") ?: JSONArray()
        val largest = List(arr.length()) {
            val item = arr.getJSONObject(it)
            DiskUsageItem(
                name = item.getString("name"),
                path = item.getString("path"),
                size = item.getLong("size"),
                directory = item.optBoolean("isDir")
            )
        }
        return DiskUsageReport(
            path = root.getString("path"),
            totalBytes = root.getLong("totalBytes"),
            fileCount = root.getInt("fileCount"),
            dirCount = root.getInt("dirCount"),
            linkCount = root.getInt("linkCount"),
            truncated = root.optBoolean("truncated"),
            largest = largest
        )
    }

    override suspend fun archiveList(path: String): ArchiveListing {
        val root = request("archive_list", JSONObject().put("path", path))
        val arr = root.getJSONArray("entries")
        val entries = List(arr.length()) {
            val e = arr.getJSONObject(it)
            ArchiveEntryInfo(e.getString("name"), e.optLong("size"), e.optBoolean("isDir"))
        }
        return ArchiveListing(
            path = root.getString("path"),
            entries = entries,
            total = root.getInt("total"),
            totalSize = root.optLong("totalSize"),
            truncated = root.optBoolean("truncated")
        )
    }

    override suspend fun archiveCreate(sources: List<String>, destDir: String, name: String, format: String): String {
        val root = request(
            "archive_create",
            JSONObject()
                .put("path", sources.firstOrNull().orEmpty())
                .put("sources", JSONArray(sources))
                .put("destDir", destDir)
                .put("name", name)
                .put("format", format),
            timeoutMs = 180_000
        )
        return root.getString("path")
    }

    override suspend fun archiveExtract(path: String, destDir: String, overwrite: Boolean): Int {
        val root = request(
            "archive_extract",
            JSONObject()
                .put("path", path)
                .put("destDir", destDir)
                .put("overwrite", overwrite),
            timeoutMs = 180_000
        )
        return root.getInt("extracted")
    }

    override suspend fun action(action: String, path: String, arguments: Map<String, String>): String =
        request(action, JSONObject(arguments).put("path", path)).optString("path", path)

    override suspend fun probe() = request("probe", JSONObject()).getString("python")
}
