package dev.itszain.termuxfilemanager

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.itszain.termuxfilemanager.data.*
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class PersistenceTest {
    private lateinit var context: Context
    private lateinit var database: AppDatabase
    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        for(name in listOf(AiSecureStore.CHATS, AiSecureStore.SESSIONS, AiSecureStore.PREF)) context.getSharedPreferences(name, 0).edit().clear().commit()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    }
    @After fun close() { database.close() }
    @Test fun upgradePreservesLinksMetadataAndCiphertextAndIsIdempotent() = runBlocking {
        val message = JSONObject().put("role", "assistant").put("text", "مرحبا").put("time", 42).put("thinking", "summary").put("custom_future_field", true)
        val chat = JSONObject().put("id", "chat1").put("name", "مشروع").put("provider", "anthropic").put("sessionId", "session1").put("agent_auto_execute", true).put("messages", JSONArray().put(message))
        val session = JSONObject().put("id", "session1").put("name", "Python").put("workdir", "~/مشروع").put("output", "old output")
        val legacy = JSONArray().put(chat).toString()
        context.getSharedPreferences(AiSecureStore.CHATS, 0).edit().putString("items", legacy).commit()
        context.getSharedPreferences(AiSecureStore.SESSIONS, 0).edit().putString("items", JSONArray().put(session).toString()).commit()
        context.getSharedPreferences(AiSecureStore.PREF, 0).edit().putString("key_anthropic", "original-iv:original-ciphertext").commit()
        LegacyMigration(context, database).run(); LegacyMigration(context, database).run()
        val imported = database.dao().allChats().single()
        assertEquals("session1", imported.sessionId); assertEquals(true, imported.autoExecute)
        assertEquals("anthropic", imported.provider); assertEquals("مشروع", imported.name)
        val importedMessage = database.dao().history("chat1").single()
        assertEquals("مرحبا", importedMessage.text); assertEquals("summary", importedMessage.thinking)
        assertTrue(JSONObject(importedMessage.metadata).getBoolean("custom_future_field"))
        assertEquals("old output", database.dao().allSessions().single().output)
        assertEquals(legacy, context.getSharedPreferences(AiSecureStore.CHATS, 0).getString("items", ""))
        assertEquals("original-iv:original-ciphertext", context.getSharedPreferences(AiSecureStore.PREF, 0).getString("key_anthropic", ""))
    }
    @Test fun malformedLegacyDataNeverMarksMigrationComplete() = runBlocking {
        context.getSharedPreferences(AiSecureStore.CHATS, 0).edit().putString("items", "broken JSON").commit()
        try { LegacyMigration(context, database).run(); fail("Expected failed import") } catch(_: org.json.JSONException) { }
        assertEquals(0, database.dao().hasMarker("legacy-v3"))
        assertTrue(database.dao().allChats().isEmpty())
        assertEquals("broken JSON", context.getSharedPreferences(AiSecureStore.CHATS, 0).getString("items", ""))
    }
    @Test fun invalidMessageRollsBackTheEntireImport() = runBlocking {
        context.getSharedPreferences(AiSecureStore.SESSIONS, 0).edit().putString("items", "[{\"id\":\"s\"}]").commit()
        context.getSharedPreferences(AiSecureStore.CHATS, 0).edit().putString("items", "[{\"id\":\"c\",\"messages\":[42]}]").commit()
        try { LegacyMigration(context, database).run(); fail("Expected failed import") } catch(_: org.json.JSONException) { }
        assertTrue(database.dao().allSessions().isEmpty()); assertTrue(database.dao().allChats().isEmpty())
    }
    @Test fun resultsSurviveScreenAbsenceAndDuplicateDeliveryDoesNotOverwriteThem() = runBlocking {
        val dao = database.dao()
        val id = dao.insertCommand(CommandRecord(ownerId = "s", label = "test", command = "echo ok", workdir = "~/"))
        fun result(text: String) = Intent().putExtra(CommandResultService.EXTRA_REQUEST_ID, id.toInt()).putExtra("result", Bundle().apply { putString("stdout", text); putInt("exitCode", 0); putInt("err", -1) })
        val gateway = TermuxGateway(context, dao)
        gateway.accept(result("first")); gateway.accept(result("duplicate"))
        assertEquals("first", dao.command(id)?.stdout); assertEquals("complete", dao.command(id)?.state)
    }
    @Test fun processRecoveryInterruptsStreamsButNeverReplaysCommands() = runBlocking {
        val dao = database.dao()
        dao.saveChat(ChatRecord("c", "name", "openai"))
        dao.saveMessage(MessageRecord("m", "c", "assistant", "partial", status = "streaming"))
        val id = dao.insertCommand(CommandRecord(ownerId = "c", label = "work", command = "dangerous-to-repeat", workdir = "~/"))
        dao.interruptStreams()
        assertEquals("interrupted", dao.history("c").single().status)
        assertEquals("partial", dao.history("c").single().text)
        assertEquals("pending", dao.command(id)?.state)
        assertNull(dao.command(id)?.completed)
    }
    @Test fun draftsAndLateResultsPersistAcrossDatabaseReopen() = runBlocking {
        val name = "reopen-test.db"; context.deleteDatabase(name)
        var disk = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        disk.dao().saveDraft(DraftRecord("~/code", "draft", "fingerprint"))
        val id = disk.dao().insertCommand(CommandRecord(ownerId = "session", label = "work", command = "echo x", workdir = "~/"))
        disk.close(); disk = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        assertEquals("draft", disk.dao().draft("~/code")?.text)
        TermuxGateway(context, disk.dao()).accept(Intent().putExtra(CommandResultService.EXTRA_REQUEST_ID, id.toInt()).putExtra("result", Bundle().apply { putString("stdout", "x"); putInt("exitCode", 0) }))
        assertEquals("x", disk.dao().command(id)?.stdout)
        disk.close(); context.deleteDatabase(name); Unit
    }
}
