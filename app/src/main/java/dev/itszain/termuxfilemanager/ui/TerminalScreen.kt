package dev.itszain.termuxfilemanager.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.itszain.termuxfilemanager.R
import dev.itszain.termuxfilemanager.features.WorkspaceModel

@Composable fun TerminalScreen(model: WorkspaceModel, incomingPath: String, incomingCommand: String, consumed: () -> Unit) {
    val sessions by model.sessions.collectAsStateWithLifecycle()
    val activeId by model.sessionId.collectAsStateWithLifecycle()
    val commands by model.commands.collectAsStateWithLifecycle()
    val current = sessions.firstOrNull { it.id == activeId }
    var command by rememberSaveable { mutableStateOf("") }
    var directory by rememberSaveable { mutableStateOf("~/") }
    var newSession by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf(false) }
    var delete by remember { mutableStateOf(false) }
    var interactive by rememberSaveable { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    var broadcast by remember { mutableStateOf(false) }
    var more by remember { mutableStateOf(false) }
    LaunchedEffect(current?.id, current?.workdir) { directory = current?.workdir ?: "~/" }
    LaunchedEffect(incomingPath, incomingCommand) { if(incomingPath.isNotBlank() || incomingCommand.isNotBlank()) { directory = incomingPath.ifBlank { "~/" }; command = incomingCommand; if(command.isNotBlank()) confirm = true; consumed() } }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ChoiceMenu(current?.name ?: stringResource(R.string.session), sessions, { it.name }, { model.selectSession(it.id) }, Modifier.weight(1f))
            IconButton(onClick = { newSession = true }) { Icon(Icons.Outlined.Add, stringResource(R.string.new_session)) }
            Box { IconButton(onClick = { more = true }) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.more)) }
                DropdownMenu(more, { more = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.rename)) }, onClick = { more = false; rename = true })
                    DropdownMenuItem(text = { Text(stringResource(R.string.delete)) }, enabled = sessions.size > 1, onClick = { more = false; delete = true })
                    DropdownMenuItem(text = { Text(stringResource(R.string.broadcast)) }, enabled = command.isNotBlank(), onClick = { more = false; broadcast = true })
                }
            }
        }
        OutlinedTextField(directory, { directory = it }, singleLine = true, label = { Text(stringResource(R.string.working_directory)) }, modifier = Modifier.fillMaxWidth(),
            trailingIcon = { IconButton(onClick = { current?.let { model.saveSession(it.copy(workdir = directory.ifBlank { "~/" })) } }) { Icon(Icons.Outlined.Save, stringResource(R.string.save)) } })
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), reverseLayout = true, verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
            items(commands, key = { it.id }) { CommandCard(it) }
            if(current?.output?.isNotBlank() == true) item { CodeText(current.output, copy = true) }
            if(commands.isEmpty() && current?.output.isNullOrBlank()) item { EmptyPane(stringResource(R.string.session_empty), stringResource(R.string.session_empty_body), Icons.Outlined.Terminal) }
        }
        OutlinedTextField(command, { command = it }, label = { Text(stringResource(R.string.command)) }, textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace), minLines = 2, maxLines = 6, modifier = Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(interactive, { interactive = it }); Text(stringResource(R.string.run_interactive), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            Button(enabled = command.isNotBlank(), onClick = { confirm = true }) { Icon(Icons.Outlined.PlayArrow, null); Text(stringResource(R.string.run)) }
        }
    }
    if(confirm || broadcast) CommandConfirmation(command, directory, broadcast, { confirm = false; broadcast = false }) {
        if(broadcast) model.broadcast(command, interactive) else model.runCommand(command, directory, interactive = interactive)
        confirm = false; broadcast = false; command = ""
    }
    if(newSession) TextEntryDialog(stringResource(R.string.new_session), label = stringResource(R.string.session_name), dismiss = { newSession = false }) { newSession = false; model.newSession(it, directory) }
    if(rename && current != null) TextEntryDialog(stringResource(R.string.rename), current.name, dismiss = { rename = false }) { rename = false; model.saveSession(current.copy(name = it)) }
    if(delete && current != null) ConfirmDialog(stringResource(R.string.delete), stringResource(R.string.delete_session_confirm), { delete = false }) { delete = false; model.deleteSession(current.id) }
}
@Composable fun CommandConfirmation(command: String, path: String, broadcast: Boolean = false, dismiss: () -> Unit, confirm: () -> Unit) {
    AlertDialog(onDismissRequest = dismiss, title = { Text(stringResource(R.string.review_command)) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(if(broadcast) R.string.broadcast_notice else R.string.command_notice))
            Text(path, style = MaterialTheme.typography.labelMedium)
            Box(Modifier.heightIn(max = 300.dp)) { androidx.compose.foundation.lazy.LazyColumn { item { CodeText(command, copy = true) } } }
        }
    }, confirmButton = { TextButton(onClick = confirm) { Text(stringResource(R.string.approve)) } }, dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.cancel)) } })
}
