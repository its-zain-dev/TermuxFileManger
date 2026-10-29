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
import org.json.JSONObject

class TermuxGateway(private val context: Context, private val dao: AppDao) : CommandGateway {
    override suspend fun submit(spec: CommandSpec): Long = withContext(Dispatchers.IO) {
        check(TermuxCommandRunner.hasPermission(context)) { "termux_permission" }
        require(spec.command.isNotBlank() || spec.kind == "files") { "empty_command" }
        val id = dao.insertCommand(CommandRecord(ownerId = spec.ownerId, label = spec.label,
            command = spec.command, workdir = spec.workdir, kind = spec.kind))
        try {
            check(id <= Int.MAX_VALUE) { "request_id_exhausted" }
            val callback = Intent(context, CommandResultService::class.java).putExtra(CommandResultService.EXTRA_REQUEST_ID, id.toInt())
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
    override suspend fun execute(spec: CommandSpec): CommandResult {
        val id = submit(spec)
        // A timeout never changes a command to 'cancelled': Termux may still be executing it.
        val result = withTimeout(120_000) { dao.observeCommand(id).filterNotNull().first { it.completed != null } }
        return CommandResult(id, result.stdout, result.stderr, result.exitCode ?: -1)
    }
    suspend fun accept(intent: Intent) {
        val id = intent.getIntExtra(CommandResultService.EXTRA_REQUEST_ID, -1).toLong()
        if (id < 0 || dao.command(id) == null) return
        val result = intent.getBundleExtra("result")
        val exit = result?.getInt("exitCode", -1) ?: -1
        val error = result?.getInt("err", -1) ?: 1
        val stdout = result?.getString("stdout").orEmpty()
        val stderr = listOf(result?.getString("stderr").orEmpty(), result?.getString("errmsg").orEmpty()).filter { it.isNotBlank() }.joinToString("\n")
        dao.completeCommand(id, if (exit == 0 && error == -1) "complete" else "failed", stdout,
            if (result == null) "missing_result" else stderr, if (error != -1) -1 else exit, System.currentTimeMillis())
    }
}

class TermuxFileRepository(private val context: Context, private val gateway: CommandGateway) : FileRepository {
    private val script by lazy { context.assets.open("file_bridge.py").bufferedReader().use { it.readText() } }
    private suspend fun request(action: String, payload: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        payload.put("version", 1)
        val result = gateway.execute(CommandSpec("$action ${payload.optString("path")}", ownerId = "files", label = action, kind = "files",
            executable = "\$PREFIX/bin/python", arguments = listOf("-c", script, action), stdin = payload.toString()))
        if (result.exitCode != 0) throw IllegalStateException(result.stderr.ifBlank { "bridge_unavailable" })
        val root = try { JSONObject(result.stdout) } catch (error: Exception) { throw FileFailure("invalid_response") }
        if (!root.optBoolean("ok")) throw FileFailure(root.optString("code", "io"), root.optString("path"))
        root
    }
    override suspend fun list(path: String, query: String, hidden: Boolean, sort: String, descending: Boolean, offset: Int): DirectoryPage {
        val root = request(if (query.isBlank()) "list" else "search", JSONObject().put("path", path).put("query", query)
            .put("showHidden", hidden).put("sort", sort).put("descending", descending).put("offset", offset).put("limit", 100))
        val items = root.getJSONArray("items")
        return DirectoryPage(root.getString("path"), List(items.length()) { FileEntry.from(items.getJSONObject(it)) },
            root.getInt("nextOffset"), root.getBoolean("more"), root.getInt("total"), root.optBoolean("truncated"))
    }
    override suspend fun read(path: String): FileContent {
        val root = request("read", JSONObject().put("path", path))
        return FileContent(path, root.getString("text"), root.getString("fingerprint"))
    }
    override suspend fun write(content: FileContent): FileContent {
        val root = request("write", JSONObject().put("path", content.path).put("text", content.text).put("fingerprint", content.fingerprint))
        return content.copy(fingerprint = root.getString("fingerprint"))
    }
    override suspend fun action(action: String, path: String, arguments: Map<String, String>): String =
        request(action, JSONObject(arguments).put("path", path)).optString("path", path)
    override suspend fun probe() = request("probe", JSONObject()).getString("python")
}
