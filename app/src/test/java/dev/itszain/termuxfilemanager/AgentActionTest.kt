package dev.itszain.termuxfilemanager

import dev.itszain.termuxfilemanager.features.AgentAction
import dev.itszain.termuxfilemanager.features.shellPath
import dev.itszain.termuxfilemanager.core.shellQuote
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import android.app.Application

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class AgentActionTest {
    @Test fun rejectsUnsupportedOrEmptyCommands() {
        assertNull(AgentAction.parse("{\"tfm_action\":{\"tool\":\"run_shell\",\"command\":\"\"}}"))
        assertNull(AgentAction.parse("{\"tfm_action\":{\"tool\":\"invented\"}}"))
    }
    @Test fun parsesFencedActionsWithNestedArguments() {
        val action = AgentAction.parse("Here is the next step:\n```json\n{\"tfm_action\":{\"tool\":\"run_shell\",\"arguments\":{\"command\":\"pwd\"}}}\n```")!!
        assertEquals("pwd", action.value("command")); assertFalse(action.unsafe())
    }
    @Test fun autoExecutionStillRequiresReviewForDestructiveCommands() {
        val action = AgentAction("run_shell", "{\"command\":\"rm -rf ~/project\"}")
        assertTrue(action.unsafe())
    }
    @Test fun shellPathsExpandOnlyTheHomePrefix() {
        assertEquals("\"\$HOME\"/'project name'", shellPath("~/project name"))
        assertEquals("'\$(touch injected)'", shellQuote("\$(touch injected)"))
        assertEquals("'a'\\''b'", shellQuote("a'b"))
    }
}
