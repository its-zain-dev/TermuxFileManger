package dev.itszain.termuxfilemanager

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.itszain.termuxfilemanager.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.util.UUID

/** Uses a dedicated disposable directory; skipped on devices without a configured Termux. */
@RunWith(AndroidJUnit4::class)
class TermuxIntegrationTest {
    @Test fun encryptedCredentialsSurviveReopeningStorage() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val provider = "integration-test"
        val value = "test-session-" + UUID.randomUUID()
        try {
            AiSecureStore.saveKey(context, provider, value)
            val packed = context.getSharedPreferences(AiSecureStore.PREF, Context.MODE_PRIVATE).getString("key_$provider", "").orEmpty()
            assertTrue(packed.contains(':'))
            assertFalse(packed.contains(value))
            val reopened = context.createPackageContext(context.packageName, 0)
            assertEquals(value, AiSecureStore.getKey(reopened, provider))
            AiSecureStore.saveKey(reopened, provider, "$value-rotated")
            assertEquals("$value-rotated", AiSecureStore.getKey(context, provider))
        } finally { AiSecureStore.saveKey(context, provider, "") }
    }

    @Test fun actualTermuxRoundTripAndKeystoreCompatibility() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Assume.assumeTrue(TermuxCommandRunner.hasPermission(context))
        val graph = context.graph(); withTimeout(20000) { graph.ready.first { it } }
        val root = "/data/data/com.termux/files/home/.tfm-test-${UUID.randomUUID()}"
        assertEquals(0, graph.commands.execute(CommandSpec("mkdir -p -- ${shellQuote(root)}", ownerId = "integration")).exitCode)
        try {
            assertTrue(graph.files.probe().isNotBlank())
            val name = "تجربة ' file.txt"
            val path = graph.files.action("create", root, mapOf("name" to name))
            val initial = graph.files.read(path)
            graph.files.write(initial.copy(text = "hello\r\nمرحبًا\n"))
            assertEquals("hello\r\nمرحبًا\n", graph.files.read(path).text)
            assertEquals(name, graph.files.list(root).entries.single().name)
            val renamed = graph.files.action("rename", path, mapOf("name" to "renamed.txt"))
            assertTrue(graph.files.list(root, query = "renamed").entries.isNotEmpty())
            val folder = graph.files.action("mkdir", root, mapOf("name" to "destination"))
            graph.files.action("copy", renamed, mapOf("destDir" to folder))
            assertEquals("hello\r\nمرحبًا\n", graph.files.read("$folder/renamed.txt").text)
            assertEquals(0, graph.commands.execute(CommandSpec("printf 'terminal-ok'", root, ownerId = "integration")).exitCode)
        } finally { graph.commands.execute(CommandSpec("rm -rf -- ${shellQuote(root)}", ownerId = "integration")) }
    }
}
