package dev.itszain.termuxfilemanager.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.itszain.termuxfilemanager.R
import dev.itszain.termuxfilemanager.features.AgentAction
import dev.itszain.termuxfilemanager.features.WorkspaceModel

@Composable
fun TerminalScreen(
    model: WorkspaceModel,
    incomingPath: String,
    incomingCommand: String,
    askAi: (String) -> Unit = {},
    consumed: () -> Unit
) {
    val sessions by model.sessions.collectAsStateWithLifecycle()
    val activeId by model.sessionId.collectAsStateWithLifecycle()
    val commands by model.commands.collectAsStateWithLifecycle()
    val snippets by model.snippets.collectAsStateWithLifecycle()
    val prefs by model.settings.collectAsStateWithLifecycle()
    val current = sessions.firstOrNull { it.id == activeId }
    val clipboard = LocalClipboardManager.current
    val cs = MaterialTheme.colorScheme

    var command by rememberSaveable { mutableStateOf("") }
    var directory by rememberSaveable { mutableStateOf("~/") }
    var newSession by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf(false) }
    var delete by remember { mutableStateOf(false) }
    var clearConfirm by remember { mutableStateOf(false) }
    var historyDialog by remember { mutableStateOf(false) }
    var addSnippetDialog by remember { mutableStateOf(false) }
    var manageSnippetsDialog by remember { mutableStateOf(false) }
    var interactive by rememberSaveable { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    var broadcast by remember { mutableStateOf(false) }
    var more by remember { mutableStateOf(false) }
    var exportedNote by remember { mutableStateOf(false) }

    LaunchedEffect(current?.id, current?.workdir) { directory = current?.workdir ?: "~/" }
    LaunchedEffect(incomingPath, incomingCommand) {
        if (incomingPath.isNotBlank() || incomingCommand.isNotBlank()) {
            directory = incomingPath.ifBlank { "~/" }
            command = incomingCommand
            if (command.isNotBlank()) confirm = true
            consumed()
        }
    }

    fun requestRun(cmd: String, runInteractive: Boolean = interactive) {
        if (cmd.isBlank()) return
        val dangerous = AgentAction("run_shell", org.json.JSONObject().put("command", cmd).toString()).unsafe()
        if (prefs.terminalConfirmRun || dangerous) {
            command = cmd
            interactive = runInteractive
            confirm = true
        } else {
            model.runCommand(cmd, directory, interactive = runInteractive)
            if (cmd == command) command = ""
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Session pill bar
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                sessions.forEach { session ->
                    FilterChip(
                        selected = session.id == activeId,
                        onClick = { model.selectSession(session.id) },
                        label = { Text(session.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingIcon = { Icon(Icons.Outlined.Terminal, null, Modifier.size(16.dp)) }
                    )
                }
            }
            IconButton(onClick = { newSession = true }) { Icon(Icons.Outlined.Add, stringResource(R.string.new_session)) }
            Box {
                IconButton(onClick = { more = true }) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.more)) }
                DropdownMenu(more, { more = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.rename)) }, leadingIcon = { Icon(Icons.Outlined.Edit, null) }, onClick = { more = false; rename = true })
                    DropdownMenuItem(text = { Text(stringResource(R.string.command_history)) }, leadingIcon = { Icon(Icons.Outlined.History, null) }, onClick = { more = false; historyDialog = true })
                    DropdownMenuItem(text = { Text(stringResource(R.string.manage_snippets)) }, leadingIcon = { Icon(Icons.Outlined.BookmarkBorder, null) }, onClick = { more = false; manageSnippetsDialog = true })
                    DropdownMenuItem(text = { Text(stringResource(R.string.export_log)) }, leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) }, onClick = {
                        more = false
                        clipboard.setText(AnnotatedString(model.exportSessionLog()))
                        exportedNote = true
                    })
                    DropdownMenuItem(text = { Text(stringResource(R.string.broadcast)) }, leadingIcon = { Icon(Icons.Outlined.Campaign, null) }, enabled = command.isNotBlank(), onClick = { more = false; broadcast = true })
                    DropdownMenuItem(text = { Text(stringResource(R.string.clear_history)) }, leadingIcon = { Icon(Icons.Outlined.ClearAll, null) }, onClick = { more = false; clearConfirm = true })
                    DropdownMenuItem(text = { Text(stringResource(R.string.delete), color = cs.error) }, leadingIcon = { Icon(Icons.Outlined.DeleteOutline, null, tint = cs.error) }, enabled = sessions.size > 1, onClick = { more = false; delete = true })
                }
            }
        }

        // Working directory + Open Termux shell button
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(
                directory, { directory = it },
                singleLine = true,
                label = { Text(stringResource(R.string.working_directory)) },
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f),
                trailingIcon = {
                    IconButton(onClick = { current?.let { model.saveSession(it.copy(workdir = directory.ifBlank { "~/" })) } }) {
                        Icon(Icons.Outlined.Save, stringResource(R.string.save))
                    }
                }
            )
            FilledTonalIconButton(
                onClick = { model.runCommand("bash -l", directory, interactive = true, label = "Interactive Shell") },
                modifier = Modifier.size(50.dp)
            ) {
                Icon(Icons.Outlined.OpenInNew, stringResource(R.string.open_terminal))
            }
        }

        // Quick command snippets strip
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            items(snippets, key = { it.id }) { snippet ->
                AssistChip(
                    onClick = {
                        if (snippet.interactive) requestRun(snippet.command, true)
                        else command = snippet.command
                    },
                    label = { Text(snippet.label, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelMedium) },
                    leadingIcon = { Icon(if (snippet.interactive) Icons.Outlined.OpenInNew else Icons.Outlined.Bolt, null, Modifier.size(15.dp)) }
                )
            }
            item(key = "add-snippet") {
                AssistChip(
                    onClick = { addSnippetDialog = true },
                    label = { Text(stringResource(R.string.add_snippet)) },
                    leadingIcon = { Icon(Icons.Outlined.Add, null, Modifier.size(15.dp)) }
                )
            }
        }

        if (exportedNote) {
            Surface(color = LocalTfmColors.current.successContainer, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.copied), color = LocalTfmColors.current.success, modifier = Modifier.weight(1f))
                    IconButton(onClick = { exportedNote = false }, modifier = Modifier.size(24.dp)) { Icon(Icons.Outlined.Close, null, Modifier.size(16.dp)) }
                }
            }
        }

        // Output timeline
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            reverseLayout = true,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 6.dp)
        ) {
            items(commands, key = { it.id }) { cmd ->
                CommandCard(
                    command = cmd,
                    onRerun = { requestRun(cmd.command, false) },
                    onEdit = { command = cmd.command; directory = cmd.workdir },
                    onAskAi = {
                        val output = listOf(cmd.stdout, cmd.stderr).filter { it.isNotBlank() }.joinToString("\n").take(3000)
                        askAi("Help me analyze or fix this Termux command in ${cmd.workdir}:\n```bash\n${cmd.command}\n```\nExit code: ${cmd.exitCode}\nOutput:\n```\n${AnsiParser.strip(output)}\n```")
                    }
                )
            }
            if (current?.output?.isNotBlank() == true) item { CodeText(current.output, copy = true, ansi = true) }
            if (commands.isEmpty() && current?.output.isNullOrBlank()) {
                item { EmptyPane(stringResource(R.string.session_empty), stringResource(R.string.session_empty_body), Icons.Outlined.Terminal) }
            }
        }

        // Command composer
        Surface(shape = RoundedCornerShape(18.dp), color = cs.surfaceContainer, border = BorderStroke(1.dp, cs.outlineVariant)) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    command, { command = it },
                    label = { Text(stringResource(R.string.command)) },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    minLines = 2, maxLines = 5,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(interactive, { interactive = it })
                    Text(stringResource(R.string.run_interactive), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    IconButton(onClick = { historyDialog = true }, enabled = commands.isNotEmpty()) {
                        Icon(Icons.Outlined.History, stringResource(R.string.command_history))
                    }
                    Button(enabled = command.isNotBlank(), onClick = { requestRun(command, interactive) }, shape = RoundedCornerShape(12.dp)) {
                        Icon(Icons.Outlined.PlayArrow, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.run))
                    }
                }
            }
        }
        Spacer(Modifier.height(2.dp))
    }

    if (confirm || broadcast) CommandConfirmation(command, directory, broadcast, { confirm = false; broadcast = false }) {
        if (broadcast) model.broadcast(command, interactive) else model.runCommand(command, directory, interactive = interactive)
        confirm = false; broadcast = false; command = ""
    }
    if (newSession) TextEntryDialog(stringResource(R.string.new_session), label = stringResource(R.string.session_name), dismiss = { newSession = false }) { newSession = false; model.newSession(it, directory) }
    if (rename && current != null) TextEntryDialog(stringResource(R.string.rename), current.name, dismiss = { rename = false }) { rename = false; model.saveSession(current.copy(name = it)) }
    if (delete && current != null) ConfirmDialog(stringResource(R.string.delete), stringResource(R.string.delete_session_confirm), { delete = false }, destructive = true) { delete = false; model.deleteSession(current.id) }
    if (clearConfirm && current != null) ConfirmDialog(stringResource(R.string.clear_history), stringResource(R.string.clear_history_confirm), { clearConfirm = false }, destructive = true) { clearConfirm = false; model.clearSessionHistory(current.id) }

    if (historyDialog) {
        var search by remember { mutableStateOf("") }
        val unique = remember(commands, search) {
            commands.map { it.command }.distinct().filter { search.isBlank() || it.contains(search, true) }
        }
        AlertDialog(
            onDismissRequest = { historyDialog = false },
            title = { Text(stringResource(R.string.command_history)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(search, { search = it }, placeholder = { Text(stringResource(R.string.search)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    LazyColumn(Modifier.heightIn(max = 320.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(unique) { past ->
                            Surface(
                                onClick = { command = past; historyDialog = false },
                                shape = RoundedCornerShape(10.dp),
                                color = cs.surfaceContainerHigh,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(past, Modifier.padding(10.dp), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { historyDialog = false }) { Text(stringResource(R.string.close)) } }
        )
    }

    if (addSnippetDialog) {
        var label by remember { mutableStateOf("") }
        var cmd by remember { mutableStateOf(command) }
        var isInteractive by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { addSnippetDialog = false },
            title = { Text(stringResource(R.string.add_snippet)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(label, { label = it }, label = { Text(stringResource(R.string.name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(cmd, { cmd = it }, label = { Text(stringResource(R.string.command)) }, minLines = 2, maxLines = 4, textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace), modifier = Modifier.fillMaxWidth())
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(isInteractive, { isInteractive = it })
                        Text(stringResource(R.string.run_interactive), style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(enabled = cmd.isNotBlank(), onClick = {
                    model.addSnippet(label.trim(), cmd.trim(), isInteractive)
                    addSnippetDialog = false
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { addSnippetDialog = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    if (manageSnippetsDialog) {
        AlertDialog(
            onDismissRequest = { manageSnippetsDialog = false },
            title = { Text(stringResource(R.string.manage_snippets)) },
            text = {
                LazyColumn(Modifier.heightIn(max = 320.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(snippets, key = { it.id }) { s ->
                        ListItem(
                            headlineContent = { Text(s.label) },
                            supportingContent = { Text(s.command, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            trailingContent = { IconButton(onClick = { model.deleteSnippet(s.id) }) { Icon(Icons.Outlined.DeleteOutline, stringResource(R.string.delete)) } }
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { manageSnippetsDialog = false }) { Text(stringResource(R.string.close)) } }
        )
    }
}

@Composable
fun CommandConfirmation(command: String, path: String, broadcast: Boolean = false, dismiss: () -> Unit, confirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(stringResource(R.string.review_command)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(if (broadcast) R.string.broadcast_notice else R.string.command_notice))
                Text(path, style = MaterialTheme.typography.labelMedium, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
                Box(Modifier.heightIn(max = 300.dp)) {
                    LazyColumn { item { TfmCodeBlock(command, language = "bash", copy = true) } }
                }
            }
        },
        confirmButton = { Button(onClick = confirm) { Text(stringResource(R.string.approve)) } },
        dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
