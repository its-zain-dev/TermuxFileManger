package dev.itszain.termuxfilemanager.features

import dev.itszain.termuxfilemanager.core.AiEvent
import org.json.JSONObject

/** Legacy compatibility only. ChatGPT uses actual function calls, not text extraction. */
data class AgentAction(val tool: String, val json: String) {
    fun value(key: String): String { val root = JSONObject(json); return root.optString(key).ifBlank { root.optJSONObject("arguments")?.optString(key).orEmpty() } }
    fun unsafe(): Boolean {
        val command = value("command").lowercase().replace("\"", "").replace("'", "")
        return Regex("\\brm\\s+.*(?:--recursive|-[a-z]*r[a-z]*)", RegexOption.DOT_MATCHES_ALL).containsMatchIn(command) ||
            Regex("\\b(mkfs(?:\\.[a-z0-9]+)?|shutdown|reboot|poweroff)\\b").containsMatchIn(command) ||
            Regex("\\bdd\\s+.*\\bof=/dev/").containsMatchIn(command) || command.contains(":(){:|:&};:")
    }
    companion object {
        fun fromCall(call: AiEvent.ToolCall): AgentAction {
            require(call.namespace in setOf("", "termux")) { "unknown_tool_namespace" }
            val name = AgentTools.canonical(call.name)
            val spec = AgentTools.find(name) ?: error("unknown_action")
            val args = JSONObject(call.arguments)
            spec.fields.forEach { require(args.opt(it) is String) { "Missing string argument: $it" } }
            return validated(args.put("tool", name)) ?: error("invalid_tool_arguments")
        }
        private fun validated(action: JSONObject): AgentAction? {
            val name = AgentTools.canonical(action.optString("tool", action.optString("name")))
            if(AgentTools.find(name) == null && name !in setOf("finish", "continue")) return null
            val result = AgentAction(name, action.put("tool", name).toString())
            val required = when(name) {
                "run_shell" -> "command"; "web_search" -> "query"; "read_file", "write_file", "create_file" -> "path"
                "save_memory" -> "content"; "install_skill" -> "url"; "activate_skill" -> "name"; else -> ""
            }
            if(required.isNotBlank() && result.value(required).isBlank()) return null
            return result
        }
        fun parse(text: String): AgentAction? = candidates(text).mapNotNull { candidate ->
            runCatching { JSONObject(candidate).optJSONObject("tfm_action")?.let(::validated) }.getOrNull()
        }.firstOrNull()
        /** Balanced objects, preserving quoted braces, escaped quotes and multiline shell strings. */
        private fun candidates(text: String): Sequence<String> = sequence {
            var start = -1; var depth = 0; var quoted = false; var escaped = false
            text.forEachIndexed { i, c ->
                if(start < 0) { if(c == '{') { start = i; depth = 1; quoted = false; escaped = false } }
                else if(quoted) {
                    if(escaped) escaped = false else if(c == '\\') escaped = true else if(c == '"') quoted = false
                } else when(c) {
                    '"' -> quoted = true
                    '{' -> depth++
                    '}' -> { depth--; if(depth == 0) { yield(text.substring(start, i + 1)); start = -1 } }
                }
            }
        }
    }
}
