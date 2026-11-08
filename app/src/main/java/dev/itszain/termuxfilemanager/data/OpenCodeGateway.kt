package dev.itszain.termuxfilemanager.data

import android.content.Context
import android.util.Base64
import dev.itszain.termuxfilemanager.AiSecureStore
import dev.itszain.termuxfilemanager.R
import dev.itszain.termuxfilemanager.core.AiEvent
import dev.itszain.termuxfilemanager.core.AiRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Streams the local OpenCode agent through its HTTP/SSE API. OpenCode owns sessions,
 * tools and permissions; the app only forwards prompts and renders the event stream.
 */
class OpenCodeGateway(private val context: Context, private val manager: OpenCodeManager) {
    private val json = "application/json; charset=utf-8".toMediaType()
    private val api = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS).readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS).build()
    private val events = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS).readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(60, TimeUnit.SECONDS).build()
    private val background = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun models(): List<String> {
        val found = manager.models()
        val preferred = runCatching { defaultModel() }.getOrNull()
        return if (preferred.isNullOrBlank()) found
        else listOf(preferred) + found.filter { it != preferred }
    }

    private suspend fun defaultModel(): String? {
        val connection = manager.connection()
        val reply = api.newCall(auth(connection, "/api/model/default").get().build()).await()
        return reply.use { response ->
            if (!response.isSuccessful) return@use null
            val raw = response.body?.string().orEmpty()
            runCatching {
                val data = JSONObject(raw).optJSONObject("data") ?: return@runCatching null
                val provider = data.optString("providerID")
                val id = data.optString("modelID").ifBlank { data.optString("id") }
                if (provider.isNotBlank() && id.isNotBlank()) "$provider/$id" else null
            }.getOrNull()
        }
    }

    fun stream(request: AiRequest): Flow<AiEvent> = callbackFlow {
        val eventCall = AtomicReference<Call?>(null)
        val sessionRef = AtomicReference<String?>(null)
        val connectionRef = AtomicReference<OpenCodeConnection?>(null)
        val finished = AtomicBoolean(false)
        val task = launch(Dispatchers.IO) {
            try {
                val connection = manager.connection()
                connectionRef.set(connection)
                val model = resolveModel(request)
                val directory = OpenCodeManager.absoluteWorkdir(request.workdir)
                val key = request.sessionKey.ifBlank { "adhoc" }
                var sessionId = manager.sessionFor(key)
                var fresh = false
                if (sessionId.isBlank() || !sessionExists(connection, sessionId)) {
                    sessionId = createSession(connection, request.sessionTitle, model, directory)
                    manager.saveSession(key, sessionId)
                    fresh = true
                } else {
                    // Keep a reused OpenCode session in sync with the app's model choice.
                    switchModel(connection, sessionId, model)
                }
                sessionRef.set(sessionId)
                val output = StringBuilder()
                val thinking = StringBuilder()
                var permissionNotice = false
                val done = CompletableDeferred<Unit>()
                val call = events.newCall(auth(connection, "/api/event").header("Accept", "text/event-stream").build())
                eventCall.set(call)
                val response = call.await()
                if (response.code == 401) {
                    response.close(); manager.invalidate()
                    throw IOException(context.getString(R.string.opencode_auth_failed))
                }
                if (!response.isSuccessful) throw failure(response)
                done.invokeOnCompletion { runCatching { response.close() } }
                val reader = launch(Dispatchers.IO) {
                    try {
                        response.use { reply ->
                            val source = reply.body?.source()
                                ?: throw IOException(context.getString(R.string.opencode_stream_unavailable))
                            while (isActive && !done.isCompleted) {
                                val line = source.readUtf8Line() ?: break
                                if (!line.startsWith("data:")) continue
                                val raw = line.substring(5).trim()
                                if (raw.isBlank()) continue
                                val event = runCatching { JSONObject(raw) }.getOrNull() ?: continue
                                val type = event.optString("type")
                                val data = event.optJSONObject("data") ?: JSONObject()
                                val eventSession = data.optString("sessionID")
                                if (eventSession.isNotBlank() && eventSession != sessionId) continue
                                when (type) {
                                    "session.text.delta" -> {
                                        val delta = data.optString("delta")
                                        if (delta.isNotEmpty()) {
                                            output.append(delta)
                                            trySendBlocking(AiEvent.Text(delta))
                                        }
                                    }
                                    "session.text.ended" -> {
                                        val text = data.optString("text")
                                        if (output.isEmpty() && text.isNotEmpty()) {
                                            output.append(text)
                                            trySendBlocking(AiEvent.Text(text))
                                        }
                                    }
                                    "session.reasoning.delta" -> {
                                        val delta = data.optString("delta")
                                        if (delta.isNotEmpty()) {
                                            thinking.append(delta)
                                            trySendBlocking(AiEvent.Thinking(delta))
                                        }
                                    }
                                    "session.reasoning.ended" -> {
                                        val text = data.optString("text")
                                        if (thinking.isEmpty() && text.isNotEmpty()) trySendBlocking(AiEvent.Thinking(text))
                                    }
                                    "session.tool.input.started" -> {
                                        val name = data.optString("name")
                                        if (name.isNotBlank()) trySendBlocking(AiEvent.Thinking("\n[OpenCode tool] $name\n"))
                                    }
                                    "session.tool.called" -> {
                                        val name = data.optString("name", data.optString("tool"))
                                        if (name.isNotBlank()) trySendBlocking(AiEvent.Thinking("\n[OpenCode tool] $name\n"))
                                    }
                                    "session.tool.failed" -> {
                                        val name = data.optString("name", data.optString("tool"))
                                        if (name.isNotBlank()) trySendBlocking(AiEvent.Thinking("\n[OpenCode tool failed] $name\n"))
                                    }
                                    "permission.asked" -> {
                                        val requestId = data.optString("id")
                                        if (requestId.isNotBlank()) {
                                            if (request.autoApprove) replyPermission(connection, sessionId, requestId, "once")
                                            else {
                                                replyPermission(connection, sessionId, requestId, "reject")
                                                if (!permissionNotice) {
                                                    permissionNotice = true
                                                    output.append("\n\n").append(context.getString(R.string.opencode_permission_rejected))
                                                }
                                            }
                                        }
                                    }
                                    "session.execution.succeeded" -> done.complete(Unit)
                                    "session.execution.failed" -> {
                                        val error = executionError(data)
                                        if (output.isBlank()) throw IOException(error.ifBlank { "opencode_run_failed" })
                                        if (error.isNotBlank()) output.append("\n\n").append(error)
                                        done.complete(Unit)
                                    }
                                    "session.execution.interrupted" -> {
                                        if (output.isBlank()) throw IOException("opencode_run_interrupted")
                                        done.complete(Unit)
                                    }
                                }
                            }
                            if (isActive && !done.isCompleted) {
                                done.completeExceptionally(IOException(context.getString(R.string.opencode_stream_unavailable)))
                            }
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        if (!done.isCompleted) done.completeExceptionally(error)
                    }
                }
                val prompt = buildPrompt(request, fresh)
                val body = JSONObject().put("text", prompt)
                attachment(request.imagePath)?.let { body.put("files", JSONArray().put(it)) }
                val promptResponse = api.newCall(auth(connection, "/api/session/${encode(sessionId)}/prompt")
                    .post(body.toString().toRequestBody(json)).build()).await()
                promptResponse.use { reply ->
                    if (reply.code == 401) {
                        manager.invalidate()
                        throw IOException(context.getString(R.string.opencode_auth_failed))
                    }
                    if (!reply.isSuccessful) throw failure(reply)
                }
                done.await()
                reader.join()
                finished.set(true)
                val text = output.toString()
                trySendBlocking(AiEvent.Complete(text.ifBlank { context.getString(R.string.opencode_no_text) }))
                close()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                close(error)
            }
        }
        awaitClose {
            eventCall.get()?.cancel()
            task.cancel()
            val sessionId = sessionRef.get()
            val connection = connectionRef.get()
            if (!finished.get() && sessionId != null && connection != null) {
                background.launch { interrupt(connection, sessionId) }
            }
        }
    }

    private suspend fun resolveModel(request: AiRequest): String {
        val configured = request.model.ifBlank { AiSecureStore.getModel(context, "opencode") }
        if (configured.isNotBlank()) return configured
        return models().firstOrNull() ?: throw IOException("missing_model")
    }

    private fun buildPrompt(request: AiRequest, fresh: Boolean): String {
        val history = request.history.filter { it.second.isNotBlank() }
        val current = history.lastOrNull()?.second.orEmpty()
        if (!fresh || history.size <= 1) return current
        val prior = StringBuilder()
        history.dropLast(1).takeLast(20).forEach { (role, text) ->
            prior.append(if (role == "assistant") "Assistant: " else "User: ").append(text.take(4000)).append("\n\n")
        }
        val context = prior.toString().let { if (it.length > 12_000) it.takeLast(12_000) else it }
        return "[CONVERSATION SO FAR]\n$context[END CONVERSATION]\n[CURRENT REQUEST]\n$current"
    }

    private fun executionError(data: JSONObject): String {
        return when (val raw = data.opt("error")) {
            is JSONObject -> raw.optString("message").ifBlank { raw.optString("type") }.take(800)
            is String -> raw.take(800)
            else -> ""
        }
    }

    private fun attachment(path: String): JSONObject? {
        if (path.isBlank()) return null
        val file = File(path)
        if (!file.isFile) return null
        val bytes = runCatching { file.readBytes() }.getOrNull() ?: return null
        if (bytes.isEmpty() || bytes.size > 10 * 1024 * 1024) return null
        val mime = when (file.extension.lowercase()) {
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            else -> "image/jpeg"
        }
        // The v2 prompt API takes PromptInput attachments: a data URI plus its source.
        return JSONObject()
            .put("uri", "data:$mime;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP))
            .put("name", file.name)
            .put("source", JSONObject().put("type", "inline"))
    }

    private fun modelJson(value: String): JSONObject {
        val trimmed = value.trim()
        val provider = if ('/' in trimmed) trimmed.substringBefore('/') else "opencode"
        val rest = if ('/' in trimmed) trimmed.substringAfter('/') else trimmed
        val id = rest.substringBefore('#').ifBlank { rest }
        val variant = rest.substringAfter('#', "")
        return JSONObject().put("providerID", provider).put("id", id).apply { if (variant.isNotBlank()) put("variant", variant) }
    }

    private suspend fun sessionExists(connection: OpenCodeConnection, sessionId: String): Boolean {
        val reply = api.newCall(auth(connection, "/api/session/${encode(sessionId)}").get().build()).await()
        return reply.use {
            if (it.code == 401) {
                manager.invalidate()
                throw IOException(context.getString(R.string.opencode_auth_failed))
            }
            it.isSuccessful
        }
    }

    private suspend fun switchModel(connection: OpenCodeConnection, sessionId: String, model: String) {
        val body = JSONObject().put("model", modelJson(model))
        val reply = api.newCall(auth(connection, "/api/session/${encode(sessionId)}/model")
            .post(body.toString().toRequestBody(json)).build()).await()
        reply.use { response ->
            if (response.code == 401) {
                manager.invalidate()
                throw IOException(context.getString(R.string.opencode_auth_failed))
            }
            if (!response.isSuccessful) throw failure(response)
        }
    }

    private suspend fun createSession(connection: OpenCodeConnection, title: String, model: String, directory: String): String {
        val payload = JSONObject()
            .put("title", title.ifBlank { "Termux File Manager" })
            .put("model", modelJson(model))
            .put("location", JSONObject().put("directory", directory))
        val reply = api.newCall(auth(connection, "/api/session").post(payload.toString().toRequestBody(json)).build()).await()
        val raw = reply.use { response ->
            if (response.code == 401) {
                manager.invalidate()
                throw IOException(context.getString(R.string.opencode_auth_failed))
            }
            if (!response.isSuccessful) throw failure(response)
            response.body?.string().orEmpty()
        }
        val id = runCatching { JSONObject(raw).optJSONObject("data")?.optString("id").orEmpty() }.getOrDefault("")
        if (id.isBlank()) throw IOException(context.getString(R.string.opencode_session_failed))
        return id
    }

    private fun replyPermission(connection: OpenCodeConnection, sessionId: String, requestId: String, decision: String) {
        runCatching {
            val body = JSONObject().put("decision", decision)
            api.newCall(auth(connection, "/api/session/${encode(sessionId)}/permission/${encode(requestId)}/reply")
                .post(body.toString().toRequestBody(json)).build()).execute().use { }
        }
    }

    private fun interrupt(connection: OpenCodeConnection, sessionId: String) {
        runCatching {
            api.newCall(auth(connection, "/api/session/${encode(sessionId)}/interrupt")
                .post("".toRequestBody(null)).build()).execute().use { }
        }
    }

    private fun auth(connection: OpenCodeConnection, path: String): Request.Builder =
        Request.Builder().url(connection.url + path)
            .header("Authorization", Credentials.basic("opencode", connection.password))
            .header("Accept", "application/json")

    private fun failure(response: Response): IOException {
        val status = response.code
        val raw = runCatching { response.body?.string().orEmpty() }.getOrDefault("")
        response.close()
        if (status == 401) manager.invalidate()
        val detail = runCatching {
            val root = JSONObject(raw)
            root.optString("message").ifBlank { root.optString("error") }
        }.getOrNull().orEmpty()
        return IOException(when {
            status == 401 -> context.getString(R.string.opencode_auth_failed)
            detail.isNotBlank() -> detail.take(800)
            else -> "HTTP $status"
        })
    }

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, error: IOException) {
                if (continuation.isActive) continuation.resumeWithException(error)
            }
            override fun onResponse(call: Call, response: Response) {
                if (continuation.isActive) continuation.resume(response) else response.close()
            }
        })
    }

    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")
}
