package dev.itszain.termuxfilemanager.data

import org.json.JSONObject

/** Replays paired function calls and outputs; interruption never replays an executable action. */
object ResponsesHistory {
    fun build(rows: List<MessageRecord>, model: String = "", account: String = ""): List<String> {
        val results = rows.filter { it.role == "tool_output" }.mapNotNull { row ->
            val id = runCatching { JSONObject(row.metadata).optString("call_id") }.getOrDefault("")
            id.takeIf { it.isNotBlank() }?.let { it to row.text }
        }.toMap()
        return buildList {
            rows.forEach { row ->
                val metadata = runCatching { JSONObject(row.metadata) }.getOrDefault(JSONObject())
                val output = metadata.optJSONArray("response_output")
                when {
                    row.role == "assistant" && row.status == "complete" && output != null -> {
                        val ids = mutableListOf<String>()
                        for(i in 0 until output.length()) {
                            val item = output.getJSONObject(i)
                            if(item.optString("type") == "reasoning" && (metadata.optString("request_model") != model || metadata.optString("request_account") != account)) continue
                            add(item.toString())
                            if(item.optString("type") == "function_call") ids += item.getString("call_id")
                        }
                        ids.forEach { id -> add(JSONObject().put("type", "function_call_output").put("call_id", id)
                            .put("output", results[id]?.take(16000) ?: "Tool execution was interrupted or declined. No automatic replay was performed.").toString()) }
                    }
                    row.role == "tool_output" && metadata.optString("call_id").isNotBlank() -> Unit
                    row.role in setOf("user", "assistant", "tool_output", "agent_control") && row.text.isNotBlank() -> {
                        val text = if(row.role == "user") metadata.optString("expanded_prompt", row.text) else if(row.role == "tool_output") "[TOOL OUTPUT]\n${row.text.take(16000)}\n[END TOOL OUTPUT]" else row.text
                        add(JSONObject().put("role", if(row.role == "assistant") "assistant" else "user").put("content", text).toString())
                    }
                }
            }
        }
    }
}
