package dev.itszain.termuxfilemanager.data

import android.content.Context
import androidx.room.withTransaction
import dev.itszain.termuxfilemanager.AiSecureStore
import org.json.JSONArray
import org.json.JSONObject

/** Transactional, repeatable import. Original preferences and ciphertext are never deleted. */
class LegacyMigration(private val context: Context, private val database: AppDatabase) {
    suspend fun run() {
        val dao = database.dao()
        if (dao.hasMarker("legacy-v3") > 0) return
        // Parse before opening the transaction. Invalid data must not silently become an empty import.
        fun read(name: String) = JSONArray(context.getSharedPreferences(name, Context.MODE_PRIVATE).getString("items", "[]"))
        val chats = read(AiSecureStore.CHATS)
        val sessions = read(AiSecureStore.SESSIONS)
        database.withTransaction {
            if (dao.hasMarker("legacy-v3") > 0) return@withTransaction
            for (index in 0 until sessions.length()) {
                val item = sessions.getJSONObject(index)
                val id = item.optString("id").ifBlank { "legacy-session-$index" }
                dao.saveSession(SessionRecord(id, item.optString("name", "Terminal"), item.optString("workdir", "~/"), item.optString("output"), item.toString()))
            }
            for (index in 0 until chats.length()) {
                val item = chats.getJSONObject(index)
                val id = item.optString("id").ifBlank { "legacy-chat-$index" }
                dao.saveChat(ChatRecord(id, item.optString("name", "Chat"), item.optString("provider", "gemini"),
                    item.optString("sessionId"), if (item.has("agent_auto_execute")) item.getBoolean("agent_auto_execute") else null, item.toString()))
                val messages = item.optJSONArray("messages") ?: JSONArray()
                for (messageIndex in 0 until messages.length()) {
                    val message = messages.getJSONObject(messageIndex)
                    dao.saveMessage(MessageRecord("legacy-$id-$messageIndex", id, message.optString("role", "user"),
                        message.optString("text"), message.optLong("time", messageIndex.toLong()),
                        message.optString("thinking"), metadata = message.toString()))
                }
            }
            check(dao.allChats().size >= chats.length()) { "Chat migration validation failed" }
            check(dao.allSessions().size >= sessions.length()) { "Session migration validation failed" }
            dao.mark(MigrationMarker("legacy-v3"))
        }
    }
}
