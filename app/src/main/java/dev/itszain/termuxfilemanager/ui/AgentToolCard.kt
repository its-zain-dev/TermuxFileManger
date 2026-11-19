package dev.itszain.termuxfilemanager.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.itszain.termuxfilemanager.R
import dev.itszain.termuxfilemanager.data.MessageRecord
import dev.itszain.termuxfilemanager.features.AgentTools
import org.json.JSONObject

@Composable
fun AgentToolCard(message: MessageRecord, approve: ((Boolean) -> Unit)? = null) {
    val metadata = runCatching { JSONObject(message.metadata) }.getOrDefault(JSONObject())
    val tool = AgentTools.find(metadata.optString("tool_name", metadata.optString("tool_label")))
    val result = message.role == "tool_output"
    val status = if (result) metadata.optString("tool_status", "complete") else message.status
    var details by remember(message.id) { mutableStateOf(false) }
    val arguments = runCatching { JSONObject(message.text) }.getOrDefault(JSONObject())
    val title = tool?.let { stringResource(it.title) } ?: metadata.optString("tool_name", "Termux")
    val failed = status in setOf("failed", "interrupted", "declined")
    val elapsedMs = metadata.optLong("elapsed_ms", -1L)
    val tfm = LocalTfmColors.current
    val cs = MaterialTheme.colorScheme

    val accentColor = when {
        status == "awaiting_approval" -> tfm.warning
        status == "running" -> tfm.info
        failed -> cs.error
        else -> tfm.success
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = cs.surfaceContainer,
        border = BorderStroke(1.dp, if (status == "awaiting_approval") tfm.warning.copy(alpha = 0.6f) else cs.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(shape = RoundedCornerShape(10.dp), color = accentColor.copy(alpha = 0.14f)) {
                    Icon(
                        when {
                            status == "awaiting_approval" -> Icons.Outlined.GppMaybe
                            failed -> Icons.Outlined.ErrorOutline
                            result -> Icons.Outlined.TaskAlt
                            else -> Icons.Outlined.Build
                        },
                        null,
                        tint = accentColor,
                        modifier = Modifier.padding(8.dp).size(18.dp)
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(
                                when (status) {
                                    "awaiting_approval" -> R.string.at_waiting
                                    "running" -> R.string.at_running
                                    "failed" -> R.string.at_failed
                                    "declined" -> R.string.at_declined
                                    "interrupted" -> R.string.at_interrupted
                                    else -> if (result) R.string.at_result else R.string.at_complete
                                }
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = accentColor
                        )
                        if (elapsedMs >= 0L) {
                            Text("· ${if (elapsedMs < 1000) "${elapsedMs}ms" else String.format("%.1fs", elapsedMs / 1000.0)}", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                        }
                    }
                }
                if (status == "running") CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            }

            if (!result) {
                arguments.optString("reason").takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = cs.onSurface)
                }
                val toolName = metadata.optString("tool_name")
                if (toolName == "edit_file") {
                    val filePath = arguments.optString("path")
                    if (filePath.isNotBlank()) {
                        Text(filePath, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall, color = cs.primary)
                    }
                    val oldStr = arguments.optString("old_text")
                    val newStr = arguments.optString("new_text")
                    if (oldStr.isNotBlank() || newStr.isNotBlank()) {
                        val previewDiff = buildString {
                            oldStr.lines().forEach { append("-").append(it).append('\n') }
                            newStr.lines().forEach { append("+").append(it).append('\n') }
                        }.trimEnd()
                        Box(Modifier.heightIn(max = 200.dp).verticalScroll(rememberScrollState())) {
                            TfmDiffView(previewDiff)
                        }
                    }
                } else {
                    arguments.optString("command", arguments.optString("path")).takeIf { it.isNotBlank() }?.let {
                        Box(Modifier.heightIn(max = 160.dp).verticalScroll(rememberScrollState())) {
                            TfmCodeBlock(it, language = if (arguments.has("command")) "bash" else "plain")
                        }
                    }
                }
            } else {
                val diff = arguments.optString("diff")
                if (diff.isNotBlank()) {
                    Box(Modifier.heightIn(max = 200.dp).verticalScroll(rememberScrollState())) {
                        TfmDiffView(diff)
                    }
                }
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { details = !details }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)) {
                    Text(stringResource(if (result) R.string.at_result else R.string.at_details), style = MaterialTheme.typography.labelMedium)
                    Icon(if (details) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, Modifier.size(18.dp))
                }
                Spacer(Modifier.weight(1f))
                if (status == "awaiting_approval" && approve != null) {
                    TextButton(onClick = { approve(false) }) { Text(stringResource(R.string.decline), color = cs.error) }
                    Spacer(Modifier.width(4.dp))
                    Button(onClick = { approve(true) }, shape = RoundedCornerShape(12.dp)) { Text(stringResource(R.string.approve)) }
                }
            }

            if (details) {
                Box(Modifier.fillMaxWidth().heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
                    TfmCodeBlock(
                        runCatching { JSONObject(message.text).toString(2) }.getOrDefault(message.text),
                        language = "json",
                        copy = true
                    )
                }
            }
        }
    }
}
