package dev.itszain.termuxfilemanager.data

import android.content.Context
import dev.itszain.termuxfilemanager.*
import dev.itszain.termuxfilemanager.core.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class ApiFailure(val status: Int, val detail: String, val retryAfter: String = "") : IOException(detail)
class ProviderGateway(private val context: Context, private val client: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(18, TimeUnit.SECONDS).readTimeout(90, TimeUnit.SECONDS)
    .followRedirects(false).followSslRedirects(false).build(),
    private val keyFor: (String) -> String = { AiSecureStore.getKey(context, it) },
    private val allowRequest: (String) -> Boolean = { AiSecureStore.allowRequest(context, it) },
    private val planToken: suspend () -> String = { context.graph().chatGpt.accessToken() }) : AiGateway {
    val modelNames = java.util.concurrent.ConcurrentHashMap<String, String>()
    override fun stream(request: AiRequest): Flow<AiEvent> = callbackFlow {
        val provider = AiProvider.byId(request.provider)
        val key = if(provider.id == "chatgpt") planToken() else keyFor(provider.id)
        require(key.isNotBlank()) { "missing_key" }
        val model = request.model.ifBlank { AiSecureStore.getModel(context, provider.id) }
        require(model.isNotBlank()) { "missing_model" }
        check(allowRequest(provider.id)) { "rate_limit" }
        val history = JSONArray().apply { request.history.forEach { (role, text) -> put(JSONObject().put("role", role).put("text", text)) } }
        val body = if(provider.id == "chatgpt") ResponsesCodec.payload(model, request, AiApiClient.systemPrompt(context, request.nativeTools.isNotEmpty())) else AiApiClient.payload(context, provider, model, history, request.imagePath,
            AiSecureStore.settings(context).getInt("output_budget_tokens", 2048))
        var address = if(provider.id == "chatgpt") "https://api.openai.com/v1/responses" else AiSecureStore.getEndpoint(context, provider.id)
        if (provider.type == "gemini") {
            address = address.replace("{model}", URLEncoder.encode(model, "UTF-8")).replace(":generateContent", ":streamGenerateContent")
            address += (if ('?' in address) "&" else "?") + "alt=sse"
        }
        val call = client.newCall(requestBuilder(provider, address, key)
            .header("Accept", "text/event-stream, application/json")
            .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType())).build())
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, error: IOException) { close(error) }
            override fun onResponse(call: Call, response: Response) {
                try {
                    response.use { reply ->
                        checkReply(reply)
                        val output = StringBuilder()
                        var completed = false
                        var hasToolCalls = false
                        val onCall: (AiEvent.ToolCall) -> Unit = { hasToolCalls = true; trySendBlocking(it) }
                        val onOutput: (List<String>) -> Unit = { trySendBlocking(AiEvent.ResponseOutput(it)) }
                        var eventName = ""
                        val listener = object : AiApiClient.StreamListener {
                            override fun onText(fragment: String) { trySendBlocking(AiEvent.Text(fragment)) }
                            override fun onThinking(fragment: String) { trySendBlocking(AiEvent.Thinking(fragment)) }
                        }
                        val source = requireNotNull(reply.body).source()
                        if (reply.header("Content-Type", "").orEmpty().contains("text/event-stream", ignoreCase = true) || source.peek().readUtf8Line()?.let { it.startsWith("data:") || it.startsWith("event:") || it.startsWith(":") } == true) {
                            val event = StringBuilder()
                            fun flush() {
                                val data = event.toString().trim(); event.setLength(0)
                                if (data.isBlank() || data == "[DONE]") return
                                if(provider.id == "chatgpt") completed = ResponsesCodec.decode(data, listener, output, eventName, onCall, onOutput) || completed else decode(provider.type, data, listener, output)
                            }
                            while (!completed && !source.exhausted() && !call.isCanceled()) {
                                val line = source.readUtf8Line() ?: break
                                if (line.isEmpty()) { flush(); eventName = "" }
                                else if (line.startsWith("event:")) eventName = line.substring(6).trim()
                                else if (line.startsWith("data:")) { if (event.isNotEmpty()) event.append('\n'); event.append(line.substring(5).trimStart()) }
                                check(event.length <= 2_000_000 && output.length <= 2_000_000) { "response_too_large" }
                            }
                            flush()
                        } else {
                            val raw = source.readUtf8()
                            if(provider.id == "chatgpt") completed = ResponsesCodec.decode(raw, listener, output, onCall = onCall, onOutput = onOutput)
                            else decode(provider.type, raw, listener, output)
                        }
                        if (!call.isCanceled()) {
                            check(provider.id != "chatgpt" || completed) { "chatgpt_incomplete" }
                            check(output.isNotBlank() || hasToolCalls) { "empty_response" }
                            trySendBlocking(AiEvent.Complete(output.toString()))
                            close()
                        }
                    }
                } catch (error: Exception) { close(error) }
            }
        })
        awaitClose { call.cancel() }
    }
    private fun decode(type: String, data: String, listener: AiApiClient.StreamListener, output: StringBuilder) {
        val root = JSONObject(data)
        if (root.has("error")) throw ApiFailure(200, root.optJSONObject("error")?.optString("message") ?: "provider_error")
        AiApiClient.decode(type, root, listener, output)
    }
    private fun requestBuilder(provider: AiProvider, address: String, key: String): Request.Builder {
        require(address.startsWith("https://")) { "https_required" }
        return Request.Builder().url(address).apply {
            when (provider.type) {
                "gemini" -> header("x-goog-api-key", key)
                "anthropic" -> { header("x-api-key", key); header("anthropic-version", "2023-06-01") }
                else -> header("Authorization", "Bearer $key")
            }
            if (provider.id == "openrouter") {
                header("HTTP-Referer", "https://github.com/its-zain-dev/TermuxFileManger")
                header("X-Title", "Termux File Manager")
            }
        }
    }
    private fun checkReply(response: Response) {
        if (response.isSuccessful) return
        val raw = response.body?.string().orEmpty()
        val detail = runCatching { JSONObject(raw).optJSONObject("error")?.optString("message") }.getOrNull()
        throw ApiFailure(response.code, detail?.take(1000) ?: "HTTP ${response.code}", response.header("Retry-After").orEmpty())
    }
    suspend fun models(providerId: String): List<String> {
        val provider = AiProvider.byId(providerId)
        val endpoint = AiSecureStore.getEndpoint(context, providerId)
        val address = when (provider.type) {
            "chatgpt_plan" -> "https://api.openai.com/v1/models"
            "gemini" -> endpoint.substringBefore("/models/") + "/models"
            "anthropic" -> endpoint.replace("/messages", "/models")
            "cohere" -> endpoint.substringBefore("/v2/") + "/v1/models?endpoint=chat"
            else -> endpoint.replace("/chat/completions", "/models")
        }
        val call = client.newCall(requestBuilder(provider, address, (if(providerId == "chatgpt") planToken() else keyFor(providerId))).get().build())
        val raw = suspendCancellableCoroutine<String> { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, error: IOException) { if (continuation.isActive) continuation.resumeWithException(error) }
                override fun onResponse(call: Call, response: Response) {
                    try { response.use { checkReply(it); val data = it.body?.string().orEmpty(); if (continuation.isActive) continuation.resume(data) } }
                    catch (error: Exception) { if (continuation.isActive) continuation.resumeWithException(error) }
                }
            })
        }
        val root = JSONObject(raw)
        val array = root.optJSONArray("data") ?: root.optJSONArray("models") ?: root.optJSONObject("data")?.optJSONArray("models") ?: JSONArray()
        val models = (0 until array.length()).mapNotNull {
            val item = array.optJSONObject(it) ?: return@mapNotNull null
            if(providerId == "chatgpt") {
                if(item.optString("visibility") != "list") return@mapNotNull null
                val slug = item.getString("slug"); modelNames[slug] = item.optString("display_name", slug); return@mapNotNull slug
            }
            if (provider.type == "gemini" && item.has("supportedGenerationMethods") && !item.getJSONArray("supportedGenerationMethods").toString().contains("generateContent")) return@mapNotNull null
            item.optString("id", item.optString("name", item.optString("model"))).removePrefix("models/").takeIf(String::isNotBlank)
        }.distinct()
        return if(providerId == "chatgpt") models else models.sorted()
    }
}
