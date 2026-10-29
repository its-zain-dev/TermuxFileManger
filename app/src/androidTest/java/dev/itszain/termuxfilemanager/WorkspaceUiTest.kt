package dev.itszain.termuxfilemanager

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkspaceUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Before fun ready() {
        compose.waitUntil(60000) { compose.activity.graph().ready.value }
        runBlocking { compose.activity.graph().settings.set("language", "en") }
        compose.waitForIdle()
    }
    @Test fun allMainDestinationsAndSettingsAreReachable() {
        compose.onAllNodesWithText("Terminal")[0].performClick()
        compose.onNodeWithText("Shell command").assertExists()
        compose.onAllNodesWithText("Assistant")[0].performClick()
        compose.onNodeWithText("What are you working on?").assertExists()
        compose.onAllNodesWithText("Tools")[0].performClick()
        compose.onNodeWithText("Working directory").assertExists()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Appearance").assertExists()
    }
    @Test fun languageAndThemeSurviveActivityRecreation() {
        runBlocking { compose.activity.graph().settings.set("language", "ar"); compose.activity.graph().settings.set("theme", "amoled") }
        compose.waitForIdle()
        compose.activityRule.scenario.recreate()
        compose.waitUntil(60000) { compose.onAllNodesWithText("الملفات").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText("الطرفية")[0].performClick()
        compose.onNodeWithText("أمر Shell").assertExists()
        runBlocking { compose.activity.graph().settings.set("language", "en"); compose.activity.graph().settings.set("theme", "system") }
    }
    @Test fun toolHubPreparesAnEditableRequestWithoutSendingIt() {
        compose.onAllNodesWithText("Tools")[0].performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Active model and session"))
        compose.onNodeWithText("Active model and session").performClick()
        compose.onNodeWithText("Prepare assistant request").performClick()
        compose.onNode(hasSetTextAction()).assertTextContains("Active model and session", substring = true)
        compose.onNodeWithContentDescription("Send").assertIsEnabled()
    }

}
