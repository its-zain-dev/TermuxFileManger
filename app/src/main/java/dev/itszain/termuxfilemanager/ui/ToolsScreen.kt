package dev.itszain.termuxfilemanager.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import dev.itszain.termuxfilemanager.AgentWorkspace
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.itszain.termuxfilemanager.R
import dev.itszain.termuxfilemanager.core.shellQuote
import dev.itszain.termuxfilemanager.features.*

@OptIn(ExperimentalLayoutApi::class)
@Composable fun ToolsScreen(model: WorkspaceModel, assistant: (String) -> Unit = {}) {
    val operations by model.toolsOutput.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    var group by rememberSaveable { mutableIntStateOf(0) }
    var agentTool by remember { mutableStateOf<AgentTool?>(null) }
    var directory by rememberSaveable { mutableStateOf("~/") }
    var custom by rememberSaveable { mutableStateOf("") }
    var selected by remember { mutableStateOf<ToolAction?>(null) }
    var command by remember { mutableStateOf<String?>(null) }
    var utility by remember { mutableStateOf<String?>(null) }
    var input by remember { mutableStateOf("") }
    var destination by remember { mutableStateOf("") }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val columns = if(maxWidth >= 680.dp) 2 else 1
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { SectionTitle(stringResource(R.string.tools_hub)); Text(stringResource(R.string.at_hub_desc), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            item { OutlinedTextField(directory, { directory = it }, label = { Text(stringResource(R.string.working_directory)) }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(query, { query = it }, label = { Text(stringResource(R.string.at_search_hint)) }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
            item { Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0 to R.string.at_all, 1 to R.string.agent_tools, 2 to R.string.packages, 3 to R.string.languages, 4 to R.string.diagnostics, 5 to R.string.project_tools).forEach { (id, label) ->
                    FilterChip(selected = group == id, onClick = { group = id }, label = { Text(stringResource(label)) })
                }
            } }
            if(group in setOf(0, 1)) {
                item { SectionTitle(stringResource(R.string.agent_tools)) }
                val tools = AgentTools.all.filter { query.isBlank() || context.getString(it.title).contains(query, true) || context.getString(it.description).contains(query, true) || it.name.contains(query, true) }
                items(tools.chunked(columns)) { row -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { tool ->
                        OutlinedCard(onClick = { agentTool = tool }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(20.dp)) {
                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Icon(if(tool.writes) Icons.Outlined.EditNote else if(tool.name == "app_info") Icons.Outlined.Info else Icons.Outlined.ManageSearch, null, tint = MaterialTheme.colorScheme.primary)
                                Text(stringResource(tool.title), style = MaterialTheme.typography.titleMedium)
                                Text(stringResource(tool.description), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(stringResource(if(!AgentTools.enabled(context, tool.name)) R.string.at_disabled else if(tool.writes) R.string.at_review else R.string.at_readonly), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    if(row.size < columns) Spacer(Modifier.weight(1f))
                } }
            }
            if(group == 0 || group in setOf(2,3,4)) {
                toolCatalog.filter { (query.isBlank() || context.getString(it.title).contains(query, true) || it.command.contains(query, true) || it.prefix.contains(query, true)) && when(group) { 2 -> it.group == R.string.packages; 3 -> it.group == R.string.languages; 4 -> it.group == R.string.diagnostics; else -> true } }.groupBy { it.group }.forEach { (label, tools) ->
                    item { SectionTitle(stringResource(label)) }
                    items(tools) { tool -> OutlinedCard(onClick = { if(tool.prefix.isBlank()) command = tool.command else selected = tool }, modifier = Modifier.fillMaxWidth()) {
                        ListItem(headlineContent = { Text(stringResource(tool.title)) }, leadingContent = { Icon(if(label == R.string.diagnostics) Icons.Outlined.MonitorHeart else if(label == R.string.languages) Icons.Outlined.Code else Icons.Outlined.Inventory2, null) }, trailingContent = { Icon(Icons.Outlined.ChevronRight, null) })
                    } }
                }
            }
            if(group in setOf(0,5)) {
                item { SectionTitle(stringResource(R.string.project_tools)) }
                items(listOf("download" to R.string.download_file, "archive" to R.string.archive_create, "extract" to R.string.archive_extract).filter { query.isBlank() || context.getString(it.second).contains(query, true) }) { (type, title) ->
                    OutlinedCard(onClick = { utility = type; input = ""; destination = "" }, modifier = Modifier.fillMaxWidth()) {
                        ListItem(headlineContent = { Text(stringResource(title)) }, leadingContent = { Icon(if(type == "download") Icons.Outlined.Download else Icons.Outlined.FolderZip, null) }, trailingContent = { Icon(Icons.Outlined.ChevronRight, null) })
                    }
                }
            }
            if(group == 0 && query.isBlank()) item {
                OutlinedCard { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Outlined.Terminal, null, tint = MaterialTheme.colorScheme.primary)
                    OutlinedTextField(custom, { custom = it }, label = { Text(stringResource(R.string.command)) }, minLines = 2, maxLines = 5, modifier = Modifier.fillMaxWidth())
                    Button(enabled = custom.isNotBlank(), onClick = { command = custom }) { Text(stringResource(R.string.review_command)) }
                } }
            }
            if(operations.isNotEmpty()) item { SectionTitle(stringResource(R.string.recent_operations)) }
            items(operations, key = { "operation-${it.id}" }) { CommandCard(it) }
        }
    }
    agentTool?.let { tool -> AlertDialog(onDismissRequest = { agentTool = null }, title = { Text(stringResource(tool.title)) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(tool.description))
            Text(stringResource(if(tool.writes) R.string.at_review else R.string.at_readonly), color = MaterialTheme.colorScheme.primary)
            if(!AgentTools.enabled(context, tool.name)) Text(stringResource(R.string.at_disabled))
        }
    }, confirmButton = { TextButton(enabled = AgentTools.enabled(context, tool.name), onClick = {
        assistant(context.getString(R.string.at_prompt, context.getString(tool.title), context.getString(tool.description)) + "\n" + context.getString(R.string.working_directory) + ": " + directory); agentTool = null
    }) { Text(stringResource(R.string.at_ask)) } }, dismissButton = { TextButton(onClick = { agentTool = null }) { Text(stringResource(R.string.close)) } }) }
    selected?.let { tool -> TextEntryDialog(stringResource(tool.title), label = stringResource(tool.hint), dismiss = { selected = null }) { command = tool.prefix + " " + shellQuote(it); selected = null } }
    command?.let { text -> CommandConfirmation(text, directory, dismiss = { command = null }) { model.runCommand(text, directory, "tools", label = "TFM Tools"); command = null } }
    if(utility != null) AlertDialog(onDismissRequest = { utility = null }, title = { Text(stringResource(R.string.project_tools)) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(input, { input = it }, label = { Text(stringResource(if(utility == "download") R.string.https_url else R.string.source_path)) })
            OutlinedTextField(destination, { destination = it }, label = { Text(stringResource(R.string.destination_path)) })
        }
    }, confirmButton = { TextButton(enabled = input.isNotBlank() && destination.isNotBlank() && (utility != "download" || input.startsWith("https://")), onClick = {
        val source = shellPath(input); val dest = shellPath(destination)
        command = when(utility) {
            "download" -> "set -e; dest=$dest; test ! -e \"\$dest\"; tmp=\$(mktemp \"\${dest}.tfm-XXXXXX\"); trap 'rm -f \"\$tmp\"' EXIT; curl --fail --location --proto '=https' --proto-redir '=https' ${shellQuote(input)} -o \"\$tmp\"; test ! -e \"\$dest\"; mv -n -- \"\$tmp\" \"\$dest\""
            "archive" -> "test ! -e $dest && tar -czf $dest -C \"\$(dirname -- $source)\" \"\$(basename -- $source)\""
            else -> "mkdir -p $dest && case $source in *.zip) unzip -n $source -d $dest ;; *.tar.gz|*.tgz|*.tar) tar --keep-old-files -xf $source -C $dest ;; *) exit 2 ;; esac"
        }
        utility = null
    }) { Text(stringResource(R.string.review_command)) } }, dismissButton = { TextButton(onClick = { utility = null }) { Text(stringResource(R.string.cancel)) } })
}
