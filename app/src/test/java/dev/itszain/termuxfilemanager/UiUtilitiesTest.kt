package dev.itszain.termuxfilemanager

import android.app.Application
import dev.itszain.termuxfilemanager.features.runnerFor
import dev.itszain.termuxfilemanager.ui.AnsiParser
import dev.itszain.termuxfilemanager.ui.SyntaxHighlighter
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class UiUtilitiesTest {
    @Test fun ansiParserStripsControlCodesAndPreservesText() {
        val raw = "\u001B[1;32mPASS\u001B[0m \u001B]0;title\u0007tests/file.py\u001B[38;5;208m!\u001B[0m"
        assertEquals("PASS tests/file.py!", AnsiParser.strip(raw))
        val parsed = AnsiParser.parse(raw, dark = true)
        assertEquals("PASS tests/file.py!", parsed.text)
        assertTrue(parsed.spanStyles.isNotEmpty())
    }

    @Test fun syntaxHighlighterDetectsLanguages() {
        assertEquals("kotlin", SyntaxHighlighter.detectLanguage("/home/Main.kt"))
        assertEquals("python", SyntaxHighlighter.detectLanguage("bridge.py"))
        assertEquals("json", SyntaxHighlighter.detectLanguage("package.json"))
        assertEquals("shell", SyntaxHighlighter.detectLanguage("install.sh"))
        assertEquals("xml", SyntaxHighlighter.detectLanguage("AndroidManifest.xml"))
        assertEquals("markdown", SyntaxHighlighter.detectLanguage("README.md"))
        assertEquals("plain", SyntaxHighlighter.detectLanguage("notes.bin"))
    }

    @Test fun runnerBuildsSafeCommandsForCommonLanguages() {
        assertEquals("python \"\$HOME\"/'app.py'", runnerFor("~/app.py"))
        assertEquals("node \"\$HOME\"/'index.js'", runnerFor("~/index.js"))
        assertEquals("go run \"\$HOME\"/'main.go'", runnerFor("~/main.go"))
        assertEquals("bash \"\$HOME\"/'run.sh'", runnerFor("~/run.sh"))
    }
}
