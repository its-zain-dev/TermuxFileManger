package dev.itszain.termuxfilemanager.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.itszain.termuxfilemanager.R
import dev.itszain.termuxfilemanager.TermuxCommandRunner
import dev.itszain.termuxfilemanager.core.FileEntry
import dev.itszain.termuxfilemanager.features.FilesModel
import kotlinx.coroutines.launch
import java.io.File
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable fun FilesScreen(model: FilesModel, openEditor: (String) -> Unit, openTerminal: (String, String) -> Unit, setup: () -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val options by model.settings.collectAsStateWithLifecycle()
    val places by model.places.collectAsStateWithLifecycle()
    val batch by model.batch.collectAsStateWithLifecycle()
    val collision by model.collision.collectAsStateWithLifecycle()
    val operations by model.operations.collectAsStateWithLifecycle()
    var history by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var query by remember { mutableStateOf("") }
    var pathDialog by remember { mutableStateOf(false) }
    var create by remember { mutableStateOf<String?>(null) }
    var sortDialog by remember { mutableStateOf(false) }
    var deleteDialog by remember { mutableStateOf(false) }
    var destinationAction by remember { mutableStateOf<String?>(null) }
    var menuEntry by remember { mutableStateOf<FileEntry?>(null) }
    var renameEntry by remember { mutableStateOf<FileEntry?>(null) }
    var permissionEntry by remember { mutableStateOf<FileEntry?>(null) }
    var details by remember { mutableStateOf<FileEntry?>(null) }
    val connected = TermuxCommandRunner.hasPermission(model.graph.context)
    LaunchedEffect(options.hidden, options.sort, options.descending, connected) { if (connected) model.refresh() }
    BackHandler(state.selection.isNotEmpty() || state.query.isNotBlank()) { if (state.selection.isNotEmpty()) model.clearSelection() else { query = ""; model.load() } }
    if (!connected) {
        EmptyPane(stringResource(R.string.connect_termux), stringResource(R.string.setup_intro), Icons.Outlined.Link) {
            Button(onClick = setup) { Text(stringResource(R.string.connect_termux)) }
        }
        return
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 840.dp
        Row(Modifier.fillMaxSize()) {
            if (wide) Column(Modifier.width(210.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(16.dp)) {
                SectionTitle(stringResource(R.string.locations))
                TextButton(onClick = { query = ""; model.load("~/") }) { Icon(Icons.Outlined.Home, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.home)) }
                TextButton(onClick = { model.load("~/storage/shared") }) { Text(stringResource(R.string.shared_storage)) }
                SectionTitle(stringResource(R.string.favorites))
                places.filter { it.favorite }.forEach { place -> TextButton(onClick = { query = ""; model.load(place.path) }) { Text(File(place.path).name.ifBlank { "/" }, maxLines = 1) } }
                SectionTitle(stringResource(R.string.recent_folders))
                places.filter { it.visited > 0 }.take(8).forEach { place -> TextButton(onClick = { query = ""; model.load(place.path) }) { Text(File(place.path).name.ifBlank { "/" }, maxLines = 1) } }
            }
            Column(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        IconButton(onClick = { query = ""; model.parent() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.up)) }
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = { model.load("/") }) { Text("/") }
                                val parts = state.path.split('/').filter(String::isNotEmpty)
                                parts.forEachIndexed { index, part ->
                                    if (index > 0) Icon(Icons.Outlined.ChevronRight, null, Modifier.size(16.dp))
                                    TextButton(onClick = { query = ""; model.load(if (state.path.startsWith("~")) parts.take(index + 1).joinToString("/") else "/" + parts.take(index + 1).joinToString("/")) }) { Text(part) }
                                }
                            }
                        }
                        IconButton(onClick = { pathDialog = true }) { Icon(Icons.Outlined.Edit, stringResource(R.string.path)) }
                    }
                }
                OutlinedTextField(query, { query = it }, placeholder = { Text(stringResource(R.string.search_files), maxLines = 1) },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) }, trailingIcon = { IconButton(onClick = { model.load(state.path, query) }) { Icon(Icons.AutoMirrored.Outlined.ArrowForward, stringResource(R.string.search)) } },
                    singleLine = true, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { model.load(state.path, query) }))
                if (!wide && places.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(places.filter { it.favorite || it.visited > 0 }.take(8), key = { it.path }) { place ->
                        SuggestionChip(onClick = { query = ""; model.load(place.path) }, label = { Text(File(place.path).name.ifBlank { "/" }) }, icon = { Icon(if (place.favorite) Icons.Outlined.Star else Icons.Outlined.History, null, Modifier.size(16.dp)) })
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (state.selection.isEmpty()) stringResource(R.string.item_count, state.total) else stringResource(R.string.selected_count, state.selection.size), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                    IconButton(onClick = { model.favorite(state.path) }) { Icon(if (places.any { it.path == state.path && it.favorite }) Icons.Outlined.Star else Icons.Outlined.StarBorder, stringResource(R.string.add_favorite)) }
                    IconButton(onClick = { history = true }) { Icon(Icons.Outlined.History, stringResource(R.string.recent_operations)) }
                    IconButton(onClick = { sortDialog = true }) { Icon(Icons.AutoMirrored.Outlined.Sort, stringResource(R.string.sort)) }
                    IconButton(onClick = { scope.launch { model.graph.settings.set("grid", !options.grid) } }) { Icon(if (options.grid) Icons.AutoMirrored.Outlined.ViewList else Icons.Outlined.GridView, stringResource(if(options.grid) R.string.list_view else R.string.grid_view)) }
                    IconButton(onClick = { model.refresh() }) { Icon(Icons.Outlined.Refresh, stringResource(R.string.refresh)) }
                }
                if (state.selection.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilledTonalButton(onClick = { destinationAction = "copy" }) { Icon(Icons.Outlined.ContentCopy, null); Text(stringResource(R.string.copy)) }
                    FilledTonalButton(onClick = { destinationAction = "move" }) { Icon(Icons.AutoMirrored.Outlined.DriveFileMove, null); Text(stringResource(R.string.move)) }
                    TextButton(onClick = { deleteDialog = true }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
                    IconButton(onClick = model::selectAll) { Icon(Icons.Outlined.SelectAll, stringResource(R.string.select_all)) }
                    IconButton(onClick = model::clearSelection) { Icon(Icons.Outlined.Close, stringResource(R.string.clear_selection)) }
                }
                state.error?.let { ErrorNotice(it, model::refresh) }
                if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (state.truncated) Text(stringResource(R.string.search_limited), style = MaterialTheme.typography.bodySmall)
                Box(Modifier.weight(1f)) {
                    if (state.entries.isEmpty() && !state.loading && state.error == null) EmptyPane(stringResource(if(state.query.isBlank()) R.string.empty_folder else R.string.no_results), if(state.query.isBlank()) stringResource(R.string.empty_folder_body) else state.query)
                    if (options.grid) LazyVerticalGrid(GridCells.Adaptive(140.dp), contentPadding = PaddingValues(bottom = 88.dp), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(state.entries, key = { it.path }) { entry ->
                            Surface(color = if(entry.path in state.selection) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                                shape = RoundedCornerShape(18.dp), modifier = Modifier.combinedClickable(onClick = { if(state.selection.isNotEmpty()) model.select(entry.path) else if(entry.directory) { query = ""; model.load(entry.path) } else openEditor(entry.path) }, onLongClick = { model.select(entry.path) })) {
                                Column(Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) { FileIcon(entry); Spacer(Modifier.weight(1f)); IconButton(onClick = { menuEntry = entry }) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.more)) } }
                                    Text(entry.name, maxLines = 2, style = MaterialTheme.typography.titleSmall)
                                    Text(if(entry.directory) entry.mode else humanSize(entry.size), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if(entry.path in state.selection) Icon(Icons.Outlined.CheckCircle, null, Modifier.padding(top = 8.dp), tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                        if(state.more) item(span = { GridItemSpan(maxLineSpan) }) { TextButton(onClick = { model.load(state.path, state.query, true) }, enabled = !state.loading) { Text(stringResource(R.string.load_more)) } }
                    } else LazyColumn(contentPadding = PaddingValues(bottom = 88.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(state.entries, key = { it.path }) { entry ->
                            ListItem(headlineContent = { Text(entry.name, maxLines = 2) }, supportingContent = { Text((if(entry.directory) entry.mode else humanSize(entry.size)) + " · " + DateFormat.getDateInstance(DateFormat.SHORT).format(Date(entry.modified))) },
                                leadingContent = { if(entry.path in state.selection) Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.primary) else FileIcon(entry) },
                                trailingContent = { IconButton(onClick = { menuEntry = entry }) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.more)) } },
                                colors = ListItemDefaults.colors(containerColor = if(entry.path in state.selection) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface),
                                modifier = Modifier.combinedClickable(onClick = { if(state.selection.isNotEmpty()) model.select(entry.path) else if(entry.directory) { query = ""; model.load(entry.path) } else openEditor(entry.path) }, onLongClick = { model.select(entry.path) }))
                        }
                        if(state.more) item { TextButton(onClick = { model.load(state.path, state.query, true) }, enabled = !state.loading, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.load_more)) } }
                    }
                    ExtendedFloatingActionButton(onClick = { create = "choose" }, icon = { Icon(Icons.Outlined.Add, null) }, text = { Text(stringResource(R.string.create)) }, modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 16.dp))
                }
            }
            if (wide && details != null) Column(Modifier.width(270.dp).padding(16.dp).verticalScroll(rememberScrollState())) {
                IconButton(onClick = { details = null }) { Icon(Icons.Outlined.Close, stringResource(R.string.close)) }
                FileDetails(details!!)
            }
        }
        if (details != null && !wide) AlertDialog(onDismissRequest = { details = null }, title = { Text(stringResource(R.string.details)) }, text = { FileDetails(details!!) }, confirmButton = { TextButton(onClick = { details = null }) { Text(stringResource(R.string.close)) } })
    }
    if (history) AlertDialog(onDismissRequest = { history = false }, title = { Text(stringResource(R.string.recent_operations)) }, text = { LazyColumn(Modifier.heightIn(max = 450.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { items(operations, key = { it.id }) { CommandCard(it) } } }, confirmButton = { TextButton(onClick = { history = false }) { Text(stringResource(R.string.close)) } })
    if (pathDialog) TextEntryDialog(stringResource(R.string.path), state.path, stringResource(R.string.path), { pathDialog = false }) { pathDialog = false; query = ""; model.load(it) }
    if (create == "choose") AlertDialog(onDismissRequest = { create = null }, title = { Text(stringResource(R.string.new_item)) }, text = {
        Column { TextButton(onClick = { create = "create" }) { Icon(Icons.AutoMirrored.Outlined.NoteAdd, null); Text(stringResource(R.string.new_file)) }; TextButton(onClick = { create = "mkdir" }) { Icon(Icons.Outlined.CreateNewFolder, null); Text(stringResource(R.string.new_folder)) } }
    }, confirmButton = {})
    if (create == "create" || create == "mkdir") TextEntryDialog(stringResource(if(create == "mkdir") R.string.new_folder else R.string.new_file), dismiss = { create = null }) { name -> val action = create!!; create = null; model.mutate(action, state.path, mapOf("name" to name)) }
    if (sortDialog) AlertDialog(onDismissRequest = { sortDialog = false }, title = { Text(stringResource(R.string.sort)) }, text = {
        Column {
            listOf("name" to R.string.name, "size" to R.string.size, "mtime" to R.string.modified).forEach { (key, label) ->
                Row(Modifier.fillMaxWidth().clickable { scope.launch { model.graph.settings.set("sort", key) } }, verticalAlignment = Alignment.CenterVertically) { RadioButton(options.sort == key, { scope.launch { model.graph.settings.set("sort", key) } }); Text(stringResource(label)) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(options.descending, { scope.launch { model.graph.settings.set("descending", it) } }); Text(stringResource(R.string.descending)) }
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(options.hidden, { scope.launch { model.graph.settings.set("hidden", it) } }); Text(stringResource(R.string.show_hidden)) }
        }
    }, confirmButton = { TextButton(onClick = { sortDialog = false }) { Text(stringResource(R.string.close)) } })
    menuEntry?.let { entry -> AlertDialog(onDismissRequest = { menuEntry = null }, title = { Text(entry.name) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            fun selected() { model.clearSelection(); model.select(entry.path); menuEntry = null }
            TextButton(onClick = { menuEntry = null; if(entry.directory) model.load(entry.path) else openEditor(entry.path) }) { Text(stringResource(R.string.open)) }
            TextButton(onClick = { selected(); destinationAction = "copy" }) { Text(stringResource(R.string.copy)) }
            TextButton(onClick = { selected(); destinationAction = "move" }) { Text(stringResource(R.string.move)) }
            TextButton(onClick = { menuEntry = null; renameEntry = entry }) { Text(stringResource(R.string.rename)) }
            TextButton(onClick = { selected(); deleteDialog = true }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            TextButton(onClick = { menuEntry = null; details = entry }) { Text(stringResource(R.string.details)) }
            TextButton(onClick = { menuEntry = null; permissionEntry = entry }) { Text(stringResource(R.string.permissions)) }
            TextButton(onClick = { clipboard.setText(AnnotatedString(entry.path)); menuEntry = null }) { Text(stringResource(R.string.copy_path)) }
            TextButton(onClick = { menuEntry = null; openTerminal(if(entry.directory) entry.path else File(entry.path).parent ?: "~/", if(entry.directory) "" else dev.itszain.termuxfilemanager.features.runnerFor(entry.path)) }) { Text(stringResource(if(entry.directory) R.string.terminal else R.string.run_file)) }
        }
    }, confirmButton = { TextButton(onClick = { menuEntry = null }) { Text(stringResource(R.string.close)) } }) }
    renameEntry?.let { entry -> TextEntryDialog(stringResource(R.string.rename), entry.name, dismiss = { renameEntry = null }) { renameEntry = null; model.mutate("rename", entry.path, mapOf("name" to it)) } }
    permissionEntry?.let { entry -> TextEntryDialog(stringResource(R.string.permissions), entry.mode, stringResource(R.string.mode_hint), { permissionEntry = null }) { permissionEntry = null; model.mutate("chmod", entry.path, mapOf("mode" to it)) } }
    if (deleteDialog) ConfirmDialog(stringResource(R.string.delete), stringResource(R.string.delete_confirm), { deleteDialog = false }) { deleteDialog = false; model.startBatch("delete") }
    destinationAction?.let { action -> DirectoryPicker(model, state.path, { destinationAction = null }) { destinationAction = null; model.startBatch(action, it) } }
    collision?.let { item ->
        var replacement by remember(item) { mutableStateOf(item.entry.name) }
        AlertDialog(onDismissRequest = { item.answer.complete(null) }, title = { Text(stringResource(R.string.conflict)) }, text = { Column { Text(stringResource(R.string.conflict_body)); OutlinedTextField(replacement, { replacement = it }, label = { Text(stringResource(R.string.name)) }) } },
            confirmButton = { TextButton(enabled = replacement.isNotBlank() && replacement != item.entry.name, onClick = { item.answer.complete(replacement) }) { Text(stringResource(R.string.rename)) } }, dismissButton = { TextButton(onClick = { item.answer.complete(null) }) { Text(stringResource(R.string.skip)) } })
    }
    batch?.let { progress -> if(collision == null) AlertDialog(onDismissRequest = { if(!progress.running) model.batch.value = null }, title = { Text(stringResource(R.string.operations)) }, text = {
        Column { Text(stringResource(R.string.progress_count, progress.done, progress.total)); if(progress.running) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 12.dp))
            LazyColumn(Modifier.heightIn(max = 250.dp)) { items(progress.results) { (name, result) -> Text("$name · ${errorText(result)}", Modifier.padding(vertical = 4.dp)) } }
        }
    }, confirmButton = { TextButton(onClick = { if(progress.running) model.stopBatch() else model.batch.value = null }) { Text(stringResource(if(progress.running) R.string.stop_remaining else R.string.close)) } }) }
}

@Composable private fun FileIcon(entry: FileEntry) {
    Icon(if(entry.directory) Icons.Outlined.Folder else if(entry.link) Icons.Outlined.Link else Icons.Outlined.Description, null,
        Modifier.size(32.dp), tint = if(entry.directory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
}
@Composable private fun FileDetails(entry: FileEntry) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FileIcon(entry); Text(entry.name, style = MaterialTheme.typography.titleLarge); CodeText(entry.path, copy = true)
        Text(stringResource(R.string.size) + ": " + humanSize(entry.size)); Text(stringResource(R.string.permissions) + ": " + entry.mode)
        Text(DateFormat.getDateTimeInstance().format(Date(entry.modified)))
        if(entry.link) { Text(stringResource(R.string.link_target)); CodeText(entry.linkTarget) }
    }
}
@Composable fun DirectoryPicker(model: FilesModel, initial: String, dismiss: () -> Unit, selected: (String) -> Unit) {
    var path by remember { mutableStateOf(initial) }
    var typed by remember { mutableStateOf(initial) }
    var entries by remember { mutableStateOf<List<FileEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(path) {
        loading = true; error = null
        try {
            var offset = 0; val all = mutableListOf<FileEntry>()
            do { val page = model.graph.files.list(path, hidden = true, offset = offset); all += page.entries.filter { it.directory }; offset = page.next } while(page.more)
            entries = all; typed = path
        } catch(failure: Exception) { error = failure.message; entries = emptyList() }
        finally { loading = false }
    }
    AlertDialog(onDismissRequest = dismiss, title = { Text(stringResource(R.string.choose_destination)) }, text = {
        Column {
            OutlinedTextField(typed, { typed = it }, label = { Text(stringResource(R.string.path)) }, singleLine = true, trailingIcon = { IconButton(onClick = { path = typed }) { Icon(Icons.AutoMirrored.Outlined.ArrowForward, stringResource(R.string.open)) } })
            TextButton(onClick = { path = File(path).parent ?: "/" }) { Text(stringResource(R.string.up)) }
            if(loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { ErrorNotice(it) }
            LazyColumn(Modifier.heightIn(max = 300.dp)) { items(entries, key = { it.path }) { entry -> ListItem(headlineContent = { Text(entry.name) }, leadingContent = { Icon(Icons.Outlined.Folder, null) }, modifier = Modifier.clickable { path = entry.path }) } }
        }
    }, confirmButton = { TextButton(enabled = !loading && error == null && typed == path, onClick = { selected(path) }) { Text(stringResource(R.string.paste_here)) } }, dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.cancel)) } })
}
