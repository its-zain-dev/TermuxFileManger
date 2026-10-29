package dev.itszain.termuxfilemanager.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.itszain.termuxfilemanager.R
import dev.itszain.termuxfilemanager.data.MessageRecord
import dev.itszain.termuxfilemanager.features.AgentTools
import org.json.JSONObject

@Composable fun AgentToolCard(message: MessageRecord, approve: ((Boolean) -> Unit)? = null) {
    val metadata = runCatching { JSONObject(message.metadata) }.getOrDefault(JSONObject())
    val tool = AgentTools.find(metadata.optString("tool_name", metadata.optString("tool_label")))
    val result = message.role == "tool_output"
    val status = if(result) metadata.optString("tool_status", "complete") else message.status
    var details by remember(message.id) { mutableStateOf(false) }
    val arguments = runCatching { JSONObject(message.text) }.getOrDefault(JSONObject())
    val title = tool?.let { stringResource(it.title) } ?: metadata.optString("tool_name", "Termux")
    val failed = status in setOf("failed", "interrupted", "declined")
    OutlinedCard(Modifier.fillMaxWidth(), colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(if(failed) Icons.Outlined.ErrorOutline else if(result) Icons.Outlined.TaskAlt else Icons.Outlined.Build, null, tint = if(failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(when(status) {
                        "awaiting_approval" -> R.string.at_waiting
                        "running" -> R.string.at_running
                        "failed" -> R.string.at_failed
                        "declined" -> R.string.at_declined
                        "interrupted" -> R.string.at_interrupted
                        else -> if(result) R.string.at_result else R.string.at_complete
                    }), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if(status == "running") CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            }
            if(!result) {
                arguments.optString("reason").takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                arguments.optString("command", arguments.optString("path")).takeIf { it.isNotBlank() }?.let {
                    Box(Modifier.heightIn(max = 160.dp).verticalScroll(rememberScrollState())) { CodeText(it) }
                }
            }
            TextButton(onClick = { details = !details }) { Text(stringResource(if(result) R.string.at_result else R.string.at_details)); Icon(if(details) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null) }
            if(details) Box(Modifier.fillMaxWidth().heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
                CodeText(runCatching { JSONObject(message.text).toString(2) }.getOrDefault(message.text), copy = true)
            }
            if(status == "awaiting_approval" && approve != null) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { approve(false) }) { Text(stringResource(R.string.decline)) }
                Button(onClick = { approve(true) }) { Text(stringResource(R.string.approve)) }
            }
        }
    }
}
