package dev.itszain.termuxfilemanager.data

import android.util.Base64
import dev.itszain.termuxfilemanager.AiApiClient
import dev.itszain.termuxfilemanager.core.AiRequest
import dev.itszain.termuxfilemanager.core.AiEvent
import dev.itszain.termuxfilemanager.features.AgentTools
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object ResponsesCodec {
    fun payload(model: String, request: AiRequest, instructions: String): JSONObject {
        val input = JSONArray()
        request.history.forEachIndexed { index, (role, text) ->
            val content = JSONArray().put(JSONObject().put("type", if(role == "assistant") "output_text" else "input_text").put("text", text))
            if(index == request.history.lastIndex && role == "user" && request.imagePath.isNotBlank()) {
                val file = File(request.imagePath); require(file.length() <= 8 * 1024 * 1024) { "image_too_large" }
                content.put(JSONObject().put("type", "input_image").put("image_url", "data:image/jpeg;base64," + Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)))
            }
            input.put(JSONObject().put("role", role).put("content", content))
        }
        val actualInput = request.responseItems?.let { items -> JSONArray().apply { items.forEach { put(JSONObject(it)) } } } ?: input
        if(request.responseItems != null && request.imagePath.isNotBlank()) {
            val file = File(request.imagePath); require(file.length() <= 8 * 1024 * 1024) { "image_too_large" }
            for(i in actualInput.length()-1 downTo 0) {
                val item = actualInput.getJSONObject(i)
                if(item.optString("role") == "user") {
                    item.put("content", JSONArray().put(JSONObject().put("type", "input_text").put("text", item.optString("content")))
                        .put(JSONObject().put("type", "input_image").put("image_url", "data:image/jpeg;base64," + Base64.encodeToString(file.readBytes(), Base64.NO_WRAP))))
                    break
                }
            }
        }
        return JSONObject().put("model", model).put("input", actualInput).put("instructions", listOf(instructions, request.context).filter { it.isNotBlank() }.joinToString("\n")).put("store", false).put("stream", true).apply {
            if(request.nativeTools.isNotEmpty()) {
                put("tools", AgentTools.schemas(request.nativeTools))
                put("parallel_tool_calls", false)
                put("include", JSONArray().put("reasoning.encrypted_content"))
            }
        }
    }
    fun decode(data: String, listener: AiApiClient.StreamListener, output: StringBuilder, eventName: String = "", onCall: (AiEvent.ToolCall) -> Unit = {}, onOutput: (List<String>) -> Unit = {}): Boolean {
        val event = JSONObject(data)
        val response = event.optJSONObject("response") ?: event
        val type = event.optString("type").ifBlank { eventName }
        if((event.has("error") && !event.isNull("error")) || response.optString("status") in setOf("failed", "incomplete")) fail(response)
        when(type) {
            "response.output_text.delta" -> { val delta = event.getString("delta"); output.append(delta); listener.onText(delta) }
            "response.reasoning_summary_text.delta" -> listener.onThinking(event.getString("delta"))
            "response.output_item.done" -> event.optJSONObject("item")?.let { toolCall(it, onCall) }
            "response.completed" -> { completed(response, listener, output, onCall, onOutput); return true }
            "response.failed", "response.incomplete", "error" -> {
                fail(response)
            }
        }
        if(type.isBlank() && response.optString("status") == "completed") {
            completed(response, listener, output, onCall, onOutput)
            return true
        }
        return false
    }
    private fun toolCall(item: JSONObject, onCall: (AiEvent.ToolCall) -> Unit) {
        if(item.optString("type") == "function_call") onCall(AiEvent.ToolCall(item.getString("call_id"), item.getString("name"), item.getString("arguments"), item.optString("namespace", "termux").takeUnless { it.isBlank() || it == "null" } ?: "termux"))
    }
    private fun completed(response: JSONObject, listener: AiApiClient.StreamListener, output: StringBuilder, onCall: (AiEvent.ToolCall) -> Unit, onOutput: (List<String>) -> Unit) {
        response.optJSONArray("output")?.let { items ->
            onOutput(List(items.length()) { items.getJSONObject(it).toString() })
            for(i in 0 until items.length()) toolCall(items.getJSONObject(i), onCall)
        }
        appendFinal(response, listener, output)
    }
    private fun appendFinal(response: JSONObject, listener: AiApiClient.StreamListener, output: StringBuilder) {
        if(output.isNotEmpty()) return
        val items = response.optJSONArray("output") ?: return
        for(i in 0 until items.length()) {
            val content = items.optJSONObject(i)?.optJSONArray("content") ?: continue
            for(j in 0 until content.length()) {
                val part = content.getJSONObject(j)
                val text = when(part.optString("type")) {
                    "output_text" -> part.optString("text")
                    "refusal" -> part.optString("refusal")
                    else -> ""
                }
                output.append(text); if(text.isNotEmpty()) listener.onText(text)
            }
        }
    }
    private fun fail(response: JSONObject): Nothing {
        val error = response.optJSONObject("error") ?: response.optJSONObject("incomplete_details") ?: response
        val detail = listOf(error.optString("code"), error.optString("reason"), error.optString("message")).filter { it.isNotBlank() }.distinct().joinToString(": ").ifBlank { "chatgpt_incomplete" }
        throw ApiFailure(200, detail.take(1000))
    }
}
