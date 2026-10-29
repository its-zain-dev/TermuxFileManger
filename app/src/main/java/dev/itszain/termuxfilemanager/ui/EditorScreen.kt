package dev.itszain.termuxfilemanager.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.itszain.termuxfilemanager.R
import dev.itszain.termuxfilemanager.features.EditorModel

@Composable fun EditorScreen(model: EditorModel, path: String, requestExit: Boolean, resetExit: () -> Unit, close: () -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    var confirmExit by remember { mutableStateOf(false) }
    var reload by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var field by remember { mutableStateOf(TextFieldValue()) }
    LaunchedEffect(path) { model.open(path) }
    LaunchedEffect(state.text) { if(field.text != state.text) field = TextFieldValue(state.text, TextRange(field.selection.end.coerceAtMost(state.text.length))) }
    fun exit() { if(state.dirty) confirmExit = true else close() }
    BackHandler { exit() }
    LaunchedEffect(requestExit) { if(requestExit) { resetExit(); exit() } }
    DisposableEffect(Unit) { onDispose { if(model.state.value.dirty) model.persistDraft() } }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(path, maxLines = 2, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            IconButton(onClick = model::undo, enabled = state.canUndo && !state.saving) { Icon(Icons.AutoMirrored.Outlined.Undo, stringResource(R.string.undo)) }
            IconButton(onClick = model::redo, enabled = state.canRedo && !state.saving) { Icon(Icons.AutoMirrored.Outlined.Redo, stringResource(R.string.redo)) }
            IconButton(onClick = { search = !search }) { Icon(Icons.Outlined.Search, stringResource(R.string.search_in_file)) }
            IconButton(onClick = { reload = true }, enabled = !state.saving) { Icon(Icons.Outlined.Refresh, stringResource(R.string.reload)) }
            Spacer(Modifier.weight(1f))
            Button(onClick = { model.save() }, enabled = state.dirty && !state.saving && !state.loading) { Icon(Icons.Outlined.Save, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.save)) }
        }
        if(search) OutlinedTextField(query, { query = it }, label = { Text(stringResource(R.string.search_in_file)) }, singleLine = true, modifier = Modifier.fillMaxWidth(), trailingIcon = {
            IconButton(onClick = { if(query.isNotEmpty()) { val match = state.text.indexOf(query, field.selection.end, ignoreCase = true).let { if(it < 0) state.text.indexOf(query, ignoreCase = true) else it }; if(match >= 0) field = field.copy(selection = TextRange(match, match + query.length)) } }) { Icon(Icons.Outlined.ArrowDownward, stringResource(R.string.search)) }
        })
        if(search && query.isNotEmpty()) Text(stringResource(R.string.match_count, Regex(Regex.escape(query), RegexOption.IGNORE_CASE).findAll(state.text).count()), style = MaterialTheme.typography.labelSmall)
        if(state.loading || state.saving) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.error?.let { ErrorNotice(it) }
        if(state.restored) Text(stringResource(R.string.draft_restored), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
        Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.weight(1f).fillMaxWidth()) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState()).padding(12.dp)) {
                    val lines = state.text.count { it == '\n' } + 1
                    Text((1..lines).joinToString("\n"), fontFamily = FontFamily.Monospace, fontSize = 14.sp, lineHeight = 22.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 16.dp))
                    BasicTextField(field, { field = it; model.edit(it.text) }, enabled = !state.loading && !state.saving && state.fingerprint.isNotBlank(),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace, fontSize = 14.sp, lineHeight = 22.sp, color = MaterialTheme.colorScheme.onSurface, textDirection = TextDirection.Ltr),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary), modifier = Modifier.widthIn(min = 280.dp).padding(bottom = 120.dp))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
            Text(stringResource(R.string.line_count, state.text.count { it == '\n' } + 1), style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.weight(1f)); if(state.dirty) Text(stringResource(R.string.unsaved), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
        }
    }
    if(confirmExit) AlertDialog(onDismissRequest = { confirmExit = false }, title = { Text(stringResource(R.string.unsaved)) }, text = { Text(stringResource(R.string.unsaved_body)) },
        confirmButton = { TextButton(onClick = { confirmExit = false; model.save(close) }) { Text(stringResource(R.string.save)) } }, dismissButton = {
            Row { TextButton(onClick = { confirmExit = false; model.persistDraft(); close() }) { Text(stringResource(R.string.keep_draft)) }; TextButton(onClick = { confirmExit = false; model.discard(close) }) { Text(stringResource(R.string.discard)) } }
        })
    if(reload) ConfirmDialog(stringResource(R.string.reload), stringResource(R.string.reload_confirm), { reload = false }) { reload = false; model.open(path, true) }
}
