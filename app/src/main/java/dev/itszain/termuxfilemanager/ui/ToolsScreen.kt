package dev.itszain.termuxfilemanager.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.itszain.termuxfilemanager.R
import dev.itszain.termuxfilemanager.TermuxCommandRunner
import dev.itszain.termuxfilemanager.core.shellQuote
import dev.itszain.termuxfilemanager.features.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ToolsScreen(model: WorkspaceModel, assistant: (String) -> Unit = {}) {
    val operations by model.toolsOutput.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val tfm = LocalTfmColors.current

    var query by rememberSaveable { mutableStateOf("") }
    var group by rememberSaveable { mutableIntStateOf(0) }
    var agentTool by remember { mutableStateOf<AgentTool?>(null) }
    var toolRevision by remember { mutableIntStateOf(0) }
    var directory by rememberSaveable { mutableStateOf("~/") }
    var custom by rememberSaveable { mutableStateOf("") }
    var selected by remember { mutableStateOf<ToolAction?>(null) }
    var pendingCommand by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var utility by remember { mutableStateOf<String?>(null) }
    var input by remember { mutableStateOf("") }
    var destination by remember { mutableStateOf("") }
    var extraHash by remember { mutableStateOf("") }

    val connected = TermuxCommandRunner.hasPermission(context)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val columns = if (maxWidth >= 680.dp) 2 else 1
        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 14.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = cs.surfaceContainer,
                    border = BorderStroke(1.dp, cs.outlineVariant),
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.tools_hub), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            TfmStatusBadge(
                                stringResource(if (connected) R.string.connected else R.string.not_connected),
                                if (connected) tfm.success else cs.error
                            )
                        }
                        Text(stringResource(R.string.at_hub_desc), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                        OutlinedTextField(
                            directory, { directory = it },
                            label = { Text(stringResource(R.string.working_directory)) },
                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            item {
                OutlinedTextField(
                    query, { query = it },
                    label = { Text(stringResource(R.string.at_search_hint)) },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    trailingIcon = if (query.isNotEmpty()) {
                        { IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Close, stringResource(R.string.close)) } }
                    } else null,
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        0 to R.string.at_all,
                        1 to R.string.agent_tools,
                        2 to R.string.packages,
                        6 to R.string.git_tools,
                        3 to R.string.languages,
                        7 to R.string.network_tools,
                        4 to R.string.diagnostics,
                        5 to R.string.project_tools
                    ).forEach { (id, label) ->
                        FilterChip(selected = group == id, onClick = { group = id }, label = { Text(stringResource(label)) })
                    }
                }
            }

            // Assistant Tools (with live toggle switches!)
            if (group in setOf(0, 1)) {
                item { SectionTitle(stringResource(R.string.agent_tools), icon = Icons.Outlined.AutoAwesome, trailing = "${AgentTools.all.size}") }
                val tools = AgentTools.all.filter {
                    query.isBlank() || context.getString(it.title).contains(query, true) ||
                        context.getString(it.description).contains(query, true) || it.name.contains(query, true)
                }
                items(tools.chunked(columns)) { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { tool ->
                            val isEnabled = remember(toolRevision, tool.name) { AgentTools.enabled(context, tool.name) }
                            Surface(
                                onClick = { agentTool = tool },
                                shape = RoundedCornerShape(18.dp),
                                color = cs.surfaceContainer,
                                border = BorderStroke(1.dp, cs.outlineVariant),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(shape = RoundedCornerShape(10.dp), color = cs.primaryContainer.copy(alpha = 0.7f)) {
                                            Icon(
                                                if (tool.writes) Icons.Outlined.EditNote else if (tool.name == "app_info") Icons.Outlined.Info else Icons.Outlined.ManageSearch,
                                                null,
                                                tint = cs.primary,
                                                modifier = Modifier.padding(7.dp).size(18.dp)
                                            )
                                        }
                                        Spacer(Modifier.weight(1f))
                                        if (tool.name != "app_info") {
                                            Switch(
                                                checked = isEnabled,
                                                onCheckedChange = { enabled ->
                                                    AgentTools.setEnabled(context, tool.name, enabled)
                                                    toolRevision++
                                                }
                                            )
                                        }
                                    }
                                    Text(stringResource(tool.title), style = MaterialTheme.typography.titleSmall)
                                    Text(stringResource(tool.description), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                                    TfmStatusBadge(
                                        stringResource(if (!isEnabled) R.string.at_disabled else if (tool.writes) R.string.at_review else R.string.at_readonly),
                                        if (!isEnabled) cs.outline else if (tool.writes) tfm.warning else tfm.success
                                    )
                                }
                            }
                        }
                        if (row.size < columns) Spacer(Modifier.weight(1f))
                    }
                }
            }

            // Catalog Groups (Packages, Git, Languages, Network, Diagnostics)
            if (group == 0 || group in setOf(2, 3, 4, 6, 7)) {
                val filtered = toolCatalog.filter {
                    (query.isBlank() || context.getString(it.title).contains(query, true) || it.command.contains(query, true) || it.prefix.contains(query, true)) &&
                        when (group) {
                            2 -> it.group == R.string.packages
                            3 -> it.group == R.string.languages
                            4 -> it.group == R.string.diagnostics
                            6 -> it.group == R.string.git_tools
                            7 -> it.group == R.string.network_tools
                            else -> true
                        }
                }
                filtered.groupBy { it.group }.forEach { (label, tools) ->
                    val sectionIcon = when (label) {
                        R.string.git_tools -> Icons.Outlined.AccountTree
                        R.string.network_tools -> Icons.Outlined.Hub
                        R.string.diagnostics -> Icons.Outlined.MonitorHeart
                        R.string.languages -> Icons.Outlined.Code
                        else -> Icons.Outlined.Inventory2
                    }
                    item { SectionTitle(stringResource(label), icon = sectionIcon) }
                    items(tools) { tool ->
                        Surface(
                            onClick = { if (tool.prefix.isBlank()) pendingCommand = tool.command to tool.interactive else selected = tool },
                            shape = RoundedCornerShape(16.dp),
                            color = cs.surfaceContainer,
                            border = BorderStroke(1.dp, cs.outlineVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Icon(sectionIcon, null, tint = cs.primary, modifier = Modifier.size(20.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(stringResource(tool.title), style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        (tool.prefix.ifBlank { tool.command }).take(60),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = cs.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                                if (tool.interactive) TfmStatusBadge("TUI", tfm.info)
                                Icon(Icons.Outlined.ChevronRight, null, tint = cs.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            // Project Utilities
            if (group in setOf(0, 5)) {
                item { SectionTitle(stringResource(R.string.project_tools), icon = Icons.Outlined.Build) }
                val utilities = listOf(
                    Triple("download", R.string.download_file, Icons.Outlined.Download),
                    Triple("archive_zip", R.string.archive_create_zip, Icons.Outlined.FolderZip),
                    Triple("archive_tar", R.string.archive_create, Icons.Outlined.FolderZip),
                    Triple("extract", R.string.archive_extract, Icons.Outlined.Unarchive)
                ).filter { query.isBlank() || context.getString(it.second).contains(query, true) }
                items(utilities) { (type, title, icon) ->
                    Surface(
                        onClick = { utility = type; input = ""; destination = ""; extraHash = "" },
                        shape = RoundedCornerShape(16.dp),
                        color = cs.surfaceContainer,
                        border = BorderStroke(1.dp, cs.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(icon, null, tint = cs.primary, modifier = Modifier.size(20.dp))
                            Text(stringResource(title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            Icon(Icons.Outlined.ChevronRight, null, tint = cs.onSurfaceVariant)
                        }
                    }
                }
            }

            if (group == 0 && query.isBlank()) {
                item {
                    Surface(shape = RoundedCornerShape(18.dp), color = cs.surfaceContainer, border = BorderStroke(1.dp, cs.outlineVariant)) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Outlined.Terminal, null, tint = cs.primary)
                                Text(stringResource(R.string.command), style = MaterialTheme.typography.titleSmall)
                            }
                            OutlinedTextField(
                                custom, { custom = it },
                                label = { Text(stringResource(R.string.command)) },
                                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                                minLines = 2, maxLines = 5,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Button(enabled = custom.isNotBlank(), onClick = { pendingCommand = custom to false }, shape = RoundedCornerShape(12.dp)) {
                                Text(stringResource(R.string.review_command))
                            }
                        }
                    }
                }
            }

            if (operations.isNotEmpty()) item { SectionTitle(stringResource(R.string.recent_operations), icon = Icons.Outlined.History) }
            items(operations, key = { "operation-${it.id}" }) { CommandCard(it) }
        }
    }

    agentTool?.let { tool ->
        val enabled = AgentTools.enabled(context, tool.name)
        AlertDialog(
            onDismissRequest = { agentTool = null },
            title = { Text(stringResource(tool.title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(tool.description))
                    TfmStatusBadge(
                        stringResource(if (!enabled) R.string.at_disabled else if (tool.writes) R.string.at_review else R.string.at_readonly),
                        if (!enabled) cs.outline else if (tool.writes) tfm.warning else tfm.success
                    )
                }
            },
            confirmButton = {
                Button(enabled = enabled, onClick = {
                    assistant(context.getString(R.string.at_prompt, context.getString(tool.title), context.getString(tool.description)) + "\n" + context.getString(R.string.working_directory) + ": " + directory)
                    agentTool = null
                }) { Text(stringResource(R.string.at_ask)) }
            },
            dismissButton = { TextButton(onClick = { agentTool = null }) { Text(stringResource(R.string.close)) } }
        )
    }

    selected?.let { tool ->
        TextEntryDialog(stringResource(tool.title), label = stringResource(tool.hint), dismiss = { selected = null }) {
            pendingCommand = (tool.prefix + " " + shellQuote(it)) to tool.interactive
            selected = null
        }
    }

    pendingCommand?.let { (text, runInteractive) ->
        CommandConfirmation(text, directory, dismiss = { pendingCommand = null }) {
            model.runCommand(text, directory, "tools", interactive = runInteractive, label = "TFM Tools")
            pendingCommand = null
        }
    }

    if (utility != null) {
        AlertDialog(
            onDismissRequest = { utility = null },
            title = { Text(stringResource(R.string.project_tools)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(input, { input = it }, label = { Text(stringResource(if (utility == "download") R.string.https_url else R.string.source_path)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(destination, { destination = it }, label = { Text(stringResource(R.string.destination_path)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    if (utility == "download") {
                        OutlinedTextField(extraHash, { extraHash = it }, label = { Text(stringResource(R.string.expected_sha256_optional)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = input.isNotBlank() && destination.isNotBlank() && (utility != "download" || input.startsWith("https://")),
                    onClick = {
                        val source = shellPath(input)
                        val dest = shellPath(destination)
                        val cmd = when (utility) {
                            "download" -> buildString {
                                append("set -e; dest=").append(dest).append("; test ! -e \"\$dest\"; tmp=\$(mktemp \"\${dest}.tfm-XXXXXX\"); trap 'rm -f \"\$tmp\"' EXIT; ")
                                append("curl --fail --location --proto '=https' --proto-redir '=https' ").append(shellQuote(input)).append(" -o \"\$tmp\"; ")
                                if (extraHash.isNotBlank()) {
                                    append("printf '%s  %s\\n' ").append(shellQuote(extraHash.trim().lowercase())).append(" \"\$tmp\" | sha256sum -c -; ")
                                }
                                append("test ! -e \"\$dest\"; mv -n -- \"\$tmp\" \"\$dest\"")
                            }
                            "archive_zip" -> "test ! -e $dest && (cd \"\$(dirname -- $source)\" && zip -r $dest \"\$(basename -- $source)\")"
                            "archive_tar" -> "test ! -e $dest && tar -czf $dest -C \"\$(dirname -- $source)\" \"\$(basename -- $source)\""
                            else -> "mkdir -p $dest && case $source in *.zip) unzip -n $source -d $dest ;; *.tar.gz|*.tgz|*.tar|*.tar.xz|*.tar.bz2) tar --keep-old-files -xf $source -C $dest ;; *) exit 2 ;; esac"
                        }
                        pendingCommand = cmd to false
                        utility = null
                    }
                ) { Text(stringResource(R.string.review_command)) }
            },
            dismissButton = { TextButton(onClick = { utility = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}
