package dev.itszain.termuxfilemanager.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.itszain.termuxfilemanager.R
import dev.itszain.termuxfilemanager.features.EditorModel
import dev.itszain.termuxfilemanager.features.runnerFor
import dev.itszain.termuxfilemanager.features.shellPath
import kotlinx.coroutines.launch
import java.io.File

private val CODING_SYMBOLS = listOf("TAB", "{", "}", "(", ")", "[", "]", "\"", "'", ";", "=", ":", "/", "\\", "$", "_", "<", ">", "|", "&", "->")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    model: EditorModel,
    path: String,
    initialLine: Int = 1,
    requestExit: Boolean,
    resetExit: () -> Unit,
    openTerminal: (String, String) -> Unit,
    close: () -> Unit
) {
    val state by model.state.collectAsStateWithLifecycle()
    val prefs by model.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val tfm = LocalTfmColors.current
    val cs = MaterialTheme.colorScheme

    var confirmExit by remember { mutableStateOf(false) }
    var reload by remember { mutableStateOf(false) }
    var forceSaveDialog by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf(false) }
    var showReplace by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var replacement by remember { mutableStateOf("") }
    var caseSensitive by remember { mutableStateOf(false) }
    var useRegex by remember { mutableStateOf(false) }
    var goToLineDialog by remember { mutableStateOf(false) }
    var diffDialog by remember { mutableStateOf(false) }
    var optionsSheet by remember { mutableStateOf(false) }
    var mdPreview by remember { mutableStateOf(false) }
    var statusNotice by remember { mutableStateOf<String?>(null) }
    var field by remember { mutableStateOf(TextFieldValue()) }
    var jumpedInitial by remember(path) { mutableStateOf(false) }

    val language = remember(path) { SyntaxHighlighter.detectLanguage(path) }
    val isMarkdown = language == "markdown"
    val isJson = language == "json"
    val canRun = remember(path) {
        path.substringAfterLast('.', "").lowercase() in setOf("py", "js", "mjs", "cjs", "ts", "sh", "bash", "rb", "php", "lua", "go", "rs", "c", "cpp")
    }

    LaunchedEffect(path) { model.open(path) }
    LaunchedEffect(state.text) {
        if (field.text != state.text) {
            val targetOffset = if (!jumpedInitial && initialLine > 1 && state.text.isNotEmpty()) {
                jumpedInitial = true
                lineStartOffset(state.text, initialLine)
            } else {
                field.selection.end.coerceAtMost(state.text.length)
            }
            field = TextFieldValue(state.text, TextRange(targetOffset))
        }
    }

    fun exit() { if (state.dirty) confirmExit = true else close() }
    BackHandler { exit() }
    LaunchedEffect(requestExit) { if (requestExit) { resetExit(); exit() } }
    DisposableEffect(Unit) { onDispose { if (model.state.value.dirty) model.persistDraft() } }

    fun insertSymbol(symbol: String) {
        if (state.largeMode || state.loading || state.saving) return
        val insert = if (symbol == "TAB") " ".repeat(prefs.editorTabSize) else symbol
        val sel = field.selection
        val start = sel.min.coerceIn(0, field.text.length)
        val end = sel.max.coerceIn(0, field.text.length)
        val updated = field.text.substring(0, start) + insert + field.text.substring(end)
        val cursor = start + insert.length
        field = TextFieldValue(updated, TextRange(cursor))
        model.edit(updated)
    }

    fun findMatches(): List<IntRange> {
        if (query.isEmpty()) return emptyList()
        val opts = if (caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
        val rx = runCatching { Regex(if (useRegex) query else Regex.escape(query), opts) }.getOrNull() ?: return emptyList()
        return rx.findAll(state.text).map { it.range }.take(1000).toList()
    }

    val matches = remember(state.text, query, caseSensitive, useRegex) { if (search) findMatches() else emptyList() }
    val (cursorLine, cursorCol) = remember(field.text, field.selection.start, state.startLine) {
        val pos = field.selection.start.coerceIn(0, field.text.length)
        val before = field.text.substring(0, pos)
        val lineIdx = before.count { it == '\n' }
        val col = pos - (before.lastIndexOf('\n') + 1) + 1
        (lineIdx + state.startLine) to col
    }

    val visualTransform = remember(language, tfm, prefs.editorSyntaxHighlight) {
        SyntaxHighlighter.visualTransformation(language, tfm, prefs.editorSyntaxHighlight)
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // File header strip
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(path, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium, fontFamily = FontFamily.Monospace, color = cs.onSurfaceVariant, modifier = Modifier.weight(1f))
            Surface(shape = RoundedCornerShape(8.dp), color = cs.surfaceContainerHigh) {
                Text(language.uppercase(), Modifier.padding(horizontal = 7.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = cs.primary)
            }
        }

        // Action toolbar
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!state.largeMode) {
                IconButton(onClick = model::undo, enabled = state.canUndo && !state.saving) { Icon(Icons.AutoMirrored.Outlined.Undo, stringResource(R.string.undo)) }
                IconButton(onClick = model::redo, enabled = state.canRedo && !state.saving) { Icon(Icons.AutoMirrored.Outlined.Redo, stringResource(R.string.redo)) }
            }
            IconButton(onClick = { search = !search }) {
                Icon(Icons.Outlined.Search, stringResource(R.string.search_in_file), tint = if (search) cs.primary else cs.onSurface)
            }
            IconButton(onClick = { goToLineDialog = true }) { Icon(Icons.Outlined.FormatListNumbered, stringResource(R.string.go_to_line)) }
            if (isMarkdown) {
                IconButton(onClick = { mdPreview = !mdPreview }) {
                    Icon(if (mdPreview) Icons.Outlined.Edit else Icons.Outlined.Visibility, stringResource(R.string.markdown_preview), tint = if (mdPreview) cs.primary else cs.onSurface)
                }
            }
            if (isJson && !state.largeMode) {
                IconButton(onClick = { statusNotice = model.formatJson() ?: "JSON formatted" }) {
                    Icon(Icons.Outlined.DataObject, stringResource(R.string.format_json))
                }
            }
            if (state.dirty) {
                IconButton(onClick = { diffDialog = true }) { Icon(Icons.Outlined.Difference, stringResource(R.string.view_diff)) }
            }
            if (canRun) {
                IconButton(onClick = { openTerminal(File(path).parent ?: "~/", runnerFor(path)) }) {
                    Icon(Icons.Outlined.PlayArrow, stringResource(R.string.run_file), tint = tfm.success)
                }
            }
            IconButton(onClick = { optionsSheet = true }) { Icon(Icons.Outlined.Tune, stringResource(R.string.editor_options)) }
            Spacer(Modifier.width(6.dp))
            if (!state.largeMode) {
                Button(
                    onClick = { model.save() },
                    enabled = state.dirty && !state.saving && !state.loading,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Outlined.Save, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.save))
                }
            }
        }

        // Large-file read-only pagination bar
        if (state.largeMode) {
            Surface(color = tfm.infoContainer, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.large_file_mode), style = MaterialTheme.typography.labelLarge, color = tfm.info)
                        Text(
                            stringResource(R.string.large_file_range, state.startLine, state.endLine, state.totalLines, humanSize(state.fileSize)),
                            style = MaterialTheme.typography.labelSmall,
                            color = cs.onSurfaceVariant
                        )
                    }
                    IconButton(enabled = state.startLine > 1 && !state.loading, onClick = { model.loadLargeRange(path, (state.startLine - 200).coerceAtLeast(1)) }) {
                        Icon(Icons.Outlined.ChevronLeft, null)
                    }
                    IconButton(enabled = state.hasMoreLines && !state.loading, onClick = { model.loadLargeRange(path, state.endLine + 1) }) {
                        Icon(Icons.Outlined.ChevronRight, null)
                    }
                    TextButton(onClick = { openTerminal(File(path).parent ?: "~/", "nano ${shellPath(path)}") }) {
                        Text("nano")
                    }
                }
            }
        }

        // Find & Replace Panel
        if (search) {
            Surface(shape = RoundedCornerShape(16.dp), color = cs.surfaceContainer, border = BorderStroke(1.dp, cs.outlineVariant), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            query, { query = it },
                            placeholder = { Text(stringResource(R.string.search_in_file)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = {
                            val prev = matches.lastOrNull { it.first < field.selection.start } ?: matches.lastOrNull()
                            if (prev != null) field = field.copy(selection = TextRange(prev.first, prev.last + 1))
                        }) { Icon(Icons.Outlined.ArrowUpward, null) }
                        IconButton(onClick = {
                            val next = matches.firstOrNull { it.first >= field.selection.end } ?: matches.firstOrNull()
                            if (next != null) field = field.copy(selection = TextRange(next.first, next.last + 1))
                        }) { Icon(Icons.Outlined.ArrowDownward, null) }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = caseSensitive, onClick = { caseSensitive = !caseSensitive }, label = { Text("Aa", fontFamily = FontFamily.Monospace) })
                        FilterChip(selected = useRegex, onClick = { useRegex = !useRegex }, label = { Text(".*", fontFamily = FontFamily.Monospace) })
                        if (!state.largeMode) {
                            FilterChip(selected = showReplace, onClick = { showReplace = !showReplace }, label = { Text(stringResource(R.string.replace)) })
                        }
                        Spacer(Modifier.weight(1f))
                        if (query.isNotEmpty()) Text(stringResource(R.string.match_count, matches.size), style = MaterialTheme.typography.labelSmall, color = cs.primary)
                    }
                    if (showReplace && !state.largeMode) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedTextField(
                                replacement, { replacement = it },
                                placeholder = { Text(stringResource(R.string.replace_with)) },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            FilledTonalButton(enabled = query.isNotEmpty(), onClick = {
                                val count = model.replaceAll(query, replacement, caseSensitive, useRegex)
                                statusNotice = "Replaced $count"
                            }) { Text(stringResource(R.string.replace_all)) }
                        }
                    }
                }
            }
        }

        if (state.loading || state.saving) LinearProgressIndicator(Modifier.fillMaxWidth())
        statusNotice?.let { note ->
            Surface(color = tfm.successContainer, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(note, style = MaterialTheme.typography.labelMedium, color = tfm.success, modifier = Modifier.weight(1f))
                    IconButton(onClick = { statusNotice = null }, modifier = Modifier.size(24.dp)) { Icon(Icons.Outlined.Close, null, Modifier.size(16.dp)) }
                }
            }
        }
        state.error?.let { err ->
            ErrorNotice(err)
            if (err == "changed") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { model.open(path, discard = true) }) { Text(stringResource(R.string.reload)) }
                    Button(onClick = { forceSaveDialog = true }) { Text(stringResource(R.string.force_overwrite)) }
                }
            }
        }
        if (state.restored) Text(stringResource(R.string.draft_restored), color = cs.primary, style = MaterialTheme.typography.labelMedium)

        // Editor / Markdown Preview Canvas
        Surface(
            color = tfm.codeBg,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, tfm.codeBorder),
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) {
            if (mdPreview && isMarkdown) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                    TfmMarkdownView(state.text) { cmd -> openTerminal(File(path).parent ?: "~/", cmd) }
                }
            } else {
                val fontSizeSp = prefs.editorFontSize.sp
                val lineHeightSp = (prefs.editorFontSize + 8).sp
                val hScroll = rememberScrollState()
                val vScroll = rememberScrollState()
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    val containerMod = if (prefs.editorWordWrap) {
                        Modifier.fillMaxSize().verticalScroll(vScroll).padding(12.dp)
                    } else {
                        Modifier.fillMaxSize().verticalScroll(vScroll).horizontalScroll(hScroll).padding(12.dp)
                    }
                    Row(containerMod) {
                        if (prefs.editorLineNumbers) {
                            val lineCount = state.text.count { it == '\n' } + 1
                            val gutter = remember(lineCount, state.startLine) {
                                (state.startLine until state.startLine + lineCount).joinToString("\n")
                            }
                            Text(
                                text = gutter,
                                fontFamily = FontFamily.Monospace,
                                fontSize = fontSizeSp,
                                lineHeight = lineHeightSp,
                                color = tfm.gutterText,
                                modifier = Modifier.padding(end = 14.dp)
                            )
                        }
                        BasicTextField(
                            value = field,
                            onValueChange = { next ->
                                if (state.largeMode) {
                                    field = field.copy(selection = next.selection)
                                } else {
                                    val handled = if (prefs.editorAutoIndent && next.text.length == field.text.length + 1 &&
                                        next.selection.start > 0 && next.text[next.selection.start - 1] == '\n'
                                    ) {
                                        val beforeNewline = next.text.substring(0, next.selection.start - 1)
                                        val prevLine = beforeNewline.substringAfterLast('\n')
                                        val indent = prevLine.takeWhile { it == ' ' || it == '\t' }
                                        if (indent.isNotEmpty()) {
                                            val pos = next.selection.start
                                            TextFieldValue(
                                                next.text.substring(0, pos) + indent + next.text.substring(pos),
                                                TextRange(pos + indent.length)
                                            )
                                        } else next
                                    } else next
                                    field = handled
                                    model.edit(handled.text)
                                }
                            },
                            enabled = !state.loading && !state.saving && (state.largeMode || state.fingerprint.isNotBlank()),
                            readOnly = state.largeMode,
                            visualTransformation = visualTransform,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = fontSizeSp,
                                lineHeight = lineHeightSp,
                                color = cs.onSurface,
                                textDirection = TextDirection.Ltr
                            ),
                            cursorBrush = SolidColor(cs.primary),
                            modifier = (if (prefs.editorWordWrap) Modifier.weight(1f) else Modifier.widthIn(min = 320.dp)).padding(bottom = 120.dp)
                        )
                    }
                }
            }
        }

        // Coding Symbol Accessory Bar
        if (!state.largeMode && !mdPreview) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CODING_SYMBOLS.forEach { sym ->
                    Surface(
                        onClick = { insertSymbol(sym) },
                        shape = RoundedCornerShape(10.dp),
                        color = cs.surfaceContainerHigh,
                        border = BorderStroke(1.dp, cs.outlineVariant)
                    ) {
                        Text(
                            sym,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp)
                        )
                    }
                }
            }
        }

        // Status footer
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Ln $cursorLine, Col $cursorCol", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
            Text("· ${state.totalLines}L · ${humanSize(state.fileSize)} · ${state.lineEnding}", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            if (state.dirty) TfmStatusBadge(stringResource(R.string.unsaved), tfm.warning)
        }
    }

    // Editor Options Bottom Sheet
    if (optionsSheet) {
        ModalBottomSheet(onDismissRequest = { optionsSheet = false }) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.editor_options), style = MaterialTheme.typography.titleMedium)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("${stringResource(R.string.font_size)}: ${prefs.editorFontSize}sp", Modifier.weight(1f))
                    IconButton(onClick = { scope.launch { model.graph.settings.set("editor_font_size", (prefs.editorFontSize - 1).coerceAtLeast(11)) } }) { Icon(Icons.Outlined.Remove, null) }
                    IconButton(onClick = { scope.launch { model.graph.settings.set("editor_font_size", (prefs.editorFontSize + 1).coerceAtMost(22)) } }) { Icon(Icons.Outlined.Add, null) }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.word_wrap), Modifier.weight(1f))
                    Switch(prefs.editorWordWrap, { scope.launch { model.graph.settings.set("editor_word_wrap", it) } })
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.line_numbers), Modifier.weight(1f))
                    Switch(prefs.editorLineNumbers, { scope.launch { model.graph.settings.set("editor_line_numbers", it) } })
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.syntax_highlight), Modifier.weight(1f))
                    Switch(prefs.editorSyntaxHighlight, { scope.launch { model.graph.settings.set("editor_syntax_highlight", it) } })
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.auto_indent), Modifier.weight(1f))
                    Switch(prefs.editorAutoIndent, { scope.launch { model.graph.settings.set("editor_auto_indent", it) } })
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.tab_size), Modifier.weight(1f))
                    listOf(2, 4).forEach { sz ->
                        FilterChip(selected = prefs.editorTabSize == sz, onClick = { scope.launch { model.graph.settings.set("editor_tab_size", sz) } }, label = { Text("$sz") })
                    }
                }
                HorizontalDivider(color = cs.outlineVariant)
                OutlinedButton(onClick = { optionsSheet = false; reload = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.reload))
                }
            }
        }
    }

    if (goToLineDialog) {
        TextEntryDialog(stringResource(R.string.go_to_line), initial = cursorLine.toString(), label = stringResource(R.string.line_number), dismiss = { goToLineDialog = false }) { raw ->
            goToLineDialog = false
            val line = raw.toIntOrNull()?.coerceAtLeast(1) ?: return@TextEntryDialog
            if (state.largeMode) {
                model.loadLargeRange(path, line)
            } else {
                val offset = lineStartOffset(state.text, line)
                field = field.copy(selection = TextRange(offset))
            }
        }
    }

    if (diffDialog) {
        val diff = remember(state.original, state.text) { model.buildUnifiedDiff() }
        AlertDialog(
            onDismissRequest = { diffDialog = false },
            title = { Text(stringResource(R.string.view_diff)) },
            text = { Box(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) { TfmDiffView(diff.ifBlank { "No changes" }) } },
            confirmButton = { TextButton(onClick = { diffDialog = false }) { Text(stringResource(R.string.close)) } }
        )
    }

    if (confirmExit) AlertDialog(
        onDismissRequest = { confirmExit = false },
        title = { Text(stringResource(R.string.unsaved)) },
        text = { Text(stringResource(R.string.unsaved_body)) },
        confirmButton = { Button(onClick = { confirmExit = false; model.save(done = close) }) { Text(stringResource(R.string.save)) } },
        dismissButton = {
            Row {
                TextButton(onClick = { confirmExit = false; model.persistDraft(); close() }) { Text(stringResource(R.string.keep_draft)) }
                TextButton(onClick = { confirmExit = false; model.discard(close) }) { Text(stringResource(R.string.discard)) }
            }
        }
    )
    if (reload) ConfirmDialog(stringResource(R.string.reload), stringResource(R.string.reload_confirm), { reload = false }) { reload = false; model.open(path, true) }
    if (forceSaveDialog) ConfirmDialog(stringResource(R.string.force_overwrite), stringResource(R.string.force_overwrite_confirm), { forceSaveDialog = false }, destructive = true) {
        forceSaveDialog = false; model.save(force = true)
    }
}

private fun lineStartOffset(text: String, targetLine: Int): Int {
    if (targetLine <= 1) return 0
    var line = 1
    for (i in text.indices) {
        if (text[i] == '\n') {
            line++
            if (line == targetLine) return (i + 1).coerceAtMost(text.length)
        }
    }
    return text.length
}
