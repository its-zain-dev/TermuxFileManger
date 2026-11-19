package dev.itszain.termuxfilemanager.ui

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.itszain.termuxfilemanager.R
import dev.itszain.termuxfilemanager.TermuxCommandRunner
import dev.itszain.termuxfilemanager.core.*
import dev.itszain.termuxfilemanager.features.FilesModel
import dev.itszain.termuxfilemanager.features.runnerFor
import kotlinx.coroutines.launch
import java.io.File
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FilesScreen(
    model: FilesModel,
    openEditor: (String, Int) -> Unit,
    openTerminal: (String, String) -> Unit,
    askAi: (String) -> Unit,
    setup: () -> Unit
) {
    val state by model.state.collectAsStateWithLifecycle()
    val options by model.settings.collectAsStateWithLifecycle()
    val places by model.places.collectAsStateWithLifecycle()
    val tabs by model.tabs.collectAsStateWithLifecycle()
    val activeTab by model.activeTabIndex.collectAsStateWithLifecycle()
    val clip by model.clipboard.collectAsStateWithLifecycle()
    val grepResult by model.grepResult.collectAsStateWithLifecycle()
    val grepLoading by model.grepLoading.collectAsStateWithLifecycle()
    val imagePreview by model.imagePreview.collectAsStateWithLifecycle()
    val imageLoading by model.imageLoading.collectAsStateWithLifecycle()
    val hashes by model.hashes.collectAsStateWithLifecycle()
    val hashesLoading by model.hashesLoading.collectAsStateWithLifecycle()
    val diskUsage by model.diskUsage.collectAsStateWithLifecycle()
    val diskUsageLoading by model.diskUsageLoading.collectAsStateWithLifecycle()
    val archiveListing by model.archiveListing.collectAsStateWithLifecycle()
    val archiveLoading by model.archiveLoading.collectAsStateWithLifecycle()
    val batch by model.batch.collectAsStateWithLifecycle()
    val collision by model.collision.collectAsStateWithLifecycle()
    val operations by model.operations.collectAsStateWithLifecycle()

    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val tfm = LocalTfmColors.current
    val cs = MaterialTheme.colorScheme

    var query by remember { mutableStateOf("") }
    var grepMode by remember { mutableStateOf(false) }
    var grepCase by remember { mutableStateOf(false) }
    var grepRegex by remember { mutableStateOf(false) }
    var history by remember { mutableStateOf(false) }
    var pathDialog by remember { mutableStateOf(false) }
    var createSheet by remember { mutableStateOf(false) }
    var createDialog by remember { mutableStateOf<String?>(null) }
    var createTemplate by remember { mutableStateOf("") }
    var symlinkDialog by remember { mutableStateOf(false) }
    var compressDialog by remember { mutableStateOf<List<FileEntry>?>(null) }
    var sortSheet by remember { mutableStateOf(false) }
    var deleteDialog by remember { mutableStateOf(false) }
    var destinationAction by remember { mutableStateOf<String?>(null) }
    var menuEntry by remember { mutableStateOf<FileEntry?>(null) }
    var renameEntry by remember { mutableStateOf<FileEntry?>(null) }
    var permissionEntry by remember { mutableStateOf<FileEntry?>(null) }
    var details by remember { mutableStateOf<FileEntry?>(null) }

    val connected = TermuxCommandRunner.hasPermission(model.graph.context)
    LaunchedEffect(options.hidden, options.sort, options.descending, connected) { if (connected) model.refresh() }

    fun openEntry(entry: FileEntry) {
        when {
            entry.directory -> { query = ""; model.load(entry.path) }
            entry.isImage -> model.loadImagePreview(entry.path)
            entry.isArchive -> model.inspectArchive(entry.path)
            else -> openEditor(entry.path, 1)
        }
    }

    val canNavigateBack = state.selection.isNotEmpty() || state.query.isNotBlank() || grepResult != null
    BackHandler(canNavigateBack) {
        when {
            state.selection.isNotEmpty() -> model.clearSelection()
            grepResult != null -> { query = ""; model.clearGrep() }
            else -> { query = ""; model.load() }
        }
    }

    if (!connected) {
        EmptyPane(stringResource(R.string.connect_termux), stringResource(R.string.setup_intro), Icons.Outlined.Link) {
            Button(onClick = setup, shape = RoundedCornerShape(14.dp)) { Text(stringResource(R.string.connect_termux)) }
        }
        return
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 840.dp
        Row(Modifier.fillMaxSize()) {
            if (wide) {
                Surface(color = cs.surfaceContainerLow, modifier = Modifier.width(228.dp).fillMaxHeight()) {
                    Column(Modifier.verticalScroll(rememberScrollState()).padding(14.dp)) {
                        SectionTitle(stringResource(R.string.locations), icon = Icons.Outlined.Explore)
                        SidebarPlaceButton(Icons.Outlined.Home, stringResource(R.string.home)) { query = ""; model.load("~/") }
                        SidebarPlaceButton(Icons.Outlined.SdStorage, stringResource(R.string.shared_storage)) { query = ""; model.load("~/storage/shared") }
                        SidebarPlaceButton(Icons.Outlined.FolderSpecial, "\$PREFIX") { query = ""; model.load("\$PREFIX") }
                        SectionTitle(stringResource(R.string.favorites), icon = Icons.Outlined.Star)
                        places.filter { it.favorite }.forEach { place ->
                            SidebarPlaceButton(Icons.Outlined.Star, File(place.path).name.ifBlank { "/" }) { query = ""; model.load(place.path) }
                        }
                        SectionTitle(stringResource(R.string.recent_folders), icon = Icons.Outlined.History)
                        places.filter { it.visited > 0 }.take(8).forEach { place ->
                            SidebarPlaceButton(Icons.Outlined.History, File(place.path).name.ifBlank { "/" }) { query = ""; model.load(place.path) }
                        }
                    }
                }
            }

            Column(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Multi-tab folder strip
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    tabs.forEachIndexed { index, tab ->
                        val selected = index == activeTab
                        InputChip(
                            selected = selected,
                            onClick = { model.selectTab(index) },
                            label = { Text(tab.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingIcon = { Icon(Icons.Outlined.Folder, null, Modifier.size(16.dp)) },
                            trailingIcon = if (tabs.size > 1) {
                                {
                                    Icon(
                                        Icons.Outlined.Close, stringResource(R.string.close),
                                        Modifier.size(15.dp).clickable { model.closeTab(index) }
                                    )
                                }
                            } else null
                        )
                    }
                    if (tabs.size < 6) {
                        IconButton(onClick = { model.addTab() }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Outlined.Add, stringResource(R.string.new_tab), Modifier.size(18.dp))
                        }
                    }
                }

                // Breadcrumbs & Git/Disk status bar
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = cs.surfaceContainer,
                    border = BorderStroke(1.dp, cs.outlineVariant)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                            IconButton(onClick = { query = ""; model.parent() }, modifier = Modifier.size(40.dp)) {
                                Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.up), Modifier.size(20.dp))
                            }
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                                    TextButton(onClick = { model.load("/") }, contentPadding = PaddingValues(horizontal = 6.dp)) {
                                        Text("/", fontFamily = FontFamily.Monospace)
                                    }
                                    val parts = state.path.split('/').filter(String::isNotEmpty)
                                    parts.forEachIndexed { index, part ->
                                        if (index > 0) Icon(Icons.Outlined.ChevronRight, null, Modifier.size(14.dp), tint = cs.onSurfaceVariant)
                                        val isLast = index == parts.lastIndex
                                        TextButton(
                                            onClick = {
                                                query = ""
                                                val target = if (state.path.startsWith("~")) parts.take(index + 1).joinToString("/")
                                                else "/" + parts.take(index + 1).joinToString("/")
                                                model.load(target)
                                            },
                                            contentPadding = PaddingValues(horizontal = 6.dp)
                                        ) {
                                            Text(
                                                part,
                                                fontWeight = if (isLast) FontWeight.SemiBold else FontWeight.Normal,
                                                color = if (isLast) cs.primary else cs.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                            IconButton(onClick = { pathDialog = true }, modifier = Modifier.size(40.dp)) {
                                Icon(Icons.Outlined.Edit, stringResource(R.string.path), Modifier.size(18.dp))
                            }
                        }
                        if (state.gitBranch.isNotBlank() || state.diskTotal > 0L) {
                            HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.6f))
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                if (state.gitBranch.isNotBlank()) {
                                    Surface(
                                        onClick = { openTerminal(state.path, "git status --short --branch") },
                                        shape = RoundedCornerShape(999.dp),
                                        color = cs.primaryContainer.copy(alpha = 0.7f)
                                    ) {
                                        Row(Modifier.padding(horizontal = 9.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                            Icon(Icons.Outlined.AccountTree, null, Modifier.size(13.dp), tint = cs.primary)
                                            Text(state.gitBranch, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = cs.onPrimaryContainer)
                                        }
                                    }
                                }
                                Spacer(Modifier.weight(1f))
                                if (state.diskTotal > 0L) {
                                    val ratio = (state.diskUsed.toFloat() / state.diskTotal.toFloat()).coerceIn(0f, 1f)
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.clickable { model.loadDiskUsage(state.path) }
                                    ) {
                                        LinearProgressIndicator(
                                            progress = { ratio },
                                            modifier = Modifier.width(52.dp).height(5.dp).clip(RoundedCornerShape(4.dp)),
                                            color = if (ratio > 0.9f) cs.error else cs.primary,
                                            trackColor = cs.surfaceContainerHighest
                                        )
                                        Text(
                                            stringResource(R.string.storage_free_short, humanSize(state.diskFree)),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = cs.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Dual-mode Search Bar (Filename + Content Grep)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = {
                            Text(
                                stringResource(if (grepMode) R.string.grep_hint else R.string.search_files),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        leadingIcon = { Icon(if (grepMode) Icons.Outlined.FindInPage else Icons.Outlined.Search, null, Modifier.size(20.dp)) },
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (query.isNotEmpty()) {
                                    IconButton(onClick = { query = ""; model.clearGrep(); if (state.query.isNotBlank()) model.load(state.path) }) {
                                        Icon(Icons.Outlined.Close, stringResource(R.string.close), Modifier.size(18.dp))
                                    }
                                }
                                IconButton(onClick = {
                                    if (grepMode) model.searchContent(query, grepCase, grepRegex)
                                    else model.load(state.path, query)
                                }) {
                                    Icon(Icons.AutoMirrored.Outlined.ArrowForward, stringResource(R.string.search), Modifier.size(20.dp))
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            if (grepMode) model.searchContent(query, grepCase, grepRegex) else model.load(state.path, query)
                        })
                    )
                    FilledTonalIconToggleButton(
                        checked = grepMode,
                        onCheckedChange = { grepMode = it; if (!it) model.clearGrep() },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Outlined.ManageSearch, stringResource(R.string.grep_mode))
                    }
                }

                if (grepMode) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        FilterChip(selected = grepCase, onClick = { grepCase = !grepCase }, label = { Text("Aa", fontFamily = FontFamily.Monospace) })
                        FilterChip(selected = grepRegex, onClick = { grepRegex = !grepRegex }, label = { Text(".*", fontFamily = FontFamily.Monospace) })
                        Text(stringResource(R.string.grep_mode), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                    }
                }

                // Category filters + Quick Places
                if (grepResult == null) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val filters = listOf(
                            "all" to R.string.filter_all,
                            "dirs" to R.string.filter_folders,
                            "code" to R.string.filter_code,
                            "docs" to R.string.filter_docs,
                            "media" to R.string.filter_media,
                            "archives" to R.string.filter_archives
                        )
                        items(filters, key = { it.first }) { (key, label) ->
                            FilterChip(
                                selected = state.filter == key,
                                onClick = { model.setFilter(key) },
                                label = { Text(stringResource(label)) }
                            )
                        }
                        if (!wide) {
                            item(key = "place-home") {
                                AssistChip(onClick = { query = ""; model.load("~/") }, label = { Text("~") }, leadingIcon = { Icon(Icons.Outlined.Home, null, Modifier.size(16.dp)) })
                            }
                            item(key = "place-shared") {
                                AssistChip(onClick = { query = ""; model.load("~/storage/shared") }, label = { Text(stringResource(R.string.shared_storage)) }, leadingIcon = { Icon(Icons.Outlined.SdStorage, null, Modifier.size(16.dp)) })
                            }
                            items(places.filter { it.favorite }.take(6), key = { "fav-${it.path}" }) { place ->
                                AssistChip(
                                    onClick = { query = ""; model.load(place.path) },
                                    label = { Text(File(place.path).name.ifBlank { "/" }) },
                                    leadingIcon = { Icon(Icons.Outlined.Star, null, Modifier.size(15.dp), tint = tfm.warning) }
                                )
                            }
                        }
                    }
                }

                // Persistent Clipboard Paste Bar
                clip?.let { activeClip ->
                    Surface(
                        color = cs.primaryContainer,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, cs.primary.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(
                                if (activeClip.action == "move") Icons.Outlined.ContentCut else Icons.Outlined.ContentCopy,
                                null, tint = cs.onPrimaryContainer, modifier = Modifier.size(18.dp)
                            )
                            Text(
                                stringResource(
                                    if (activeClip.action == "move") R.string.clipboard_move_ready else R.string.clipboard_copy_ready,
                                    activeClip.entries.size
                                ),
                                style = MaterialTheme.typography.labelLarge,
                                color = cs.onPrimaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                            Button(onClick = { model.pasteClipboard(state.path) }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                                Text(stringResource(R.string.paste_here))
                            }
                            IconButton(onClick = model::clearClipboard, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Outlined.Close, stringResource(R.string.cancel), tint = cs.onPrimaryContainer, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }

                // Action bar row
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        when {
                            grepResult != null -> stringResource(R.string.grep_summary, grepResult!!.total, grepResult!!.filesSearched)
                            state.selection.isNotEmpty() -> stringResource(R.string.selected_count, state.selection.size)
                            else -> stringResource(R.string.item_count, state.total)
                        },
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.labelLarge,
                        color = cs.onSurfaceVariant
                    )
                    IconButton(onClick = { model.favorite(state.path) }) {
                        val isFav = places.any { it.path == state.path && it.favorite }
                        Icon(if (isFav) Icons.Outlined.Star else Icons.Outlined.StarBorder, stringResource(R.string.add_favorite), tint = if (isFav) tfm.warning else cs.onSurfaceVariant)
                    }
                    IconButton(onClick = { model.loadDiskUsage(state.path) }) {
                        Icon(Icons.Outlined.PieChart, stringResource(R.string.disk_usage))
                    }
                    IconButton(onClick = { history = true }) { Icon(Icons.Outlined.History, stringResource(R.string.recent_operations)) }
                    IconButton(onClick = { sortSheet = true }) { Icon(Icons.AutoMirrored.Outlined.Sort, stringResource(R.string.sort)) }
                    IconButton(onClick = { scope.launch { model.graph.settings.set("grid", !options.grid) } }) {
                        Icon(if (options.grid) Icons.AutoMirrored.Outlined.ViewList else Icons.Outlined.GridView, stringResource(if (options.grid) R.string.list_view else R.string.grid_view))
                    }
                    IconButton(onClick = { model.refresh() }) { Icon(Icons.Outlined.Refresh, stringResource(R.string.refresh)) }
                }

                // Multi-select action bar
                if (state.selection.isNotEmpty()) {
                    Surface(shape = RoundedCornerShape(16.dp), color = cs.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
                        FlowRow(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilledTonalButton(onClick = { model.copyToClipboard() }) {
                                Icon(Icons.Outlined.ContentCopy, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.copy))
                            }
                            FilledTonalButton(onClick = { model.cutToClipboard() }) {
                                Icon(Icons.Outlined.ContentCut, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.move))
                            }
                            FilledTonalButton(onClick = { compressDialog = model.selectedEntries() }) {
                                Icon(Icons.Outlined.FolderZip, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.compress))
                            }
                            OutlinedButton(onClick = { destinationAction = "copy" }) { Text(stringResource(R.string.copy_to)) }
                            TextButton(onClick = { deleteDialog = true }) { Text(stringResource(R.string.delete), color = cs.error) }
                            IconButton(onClick = model::selectAll) { Icon(Icons.Outlined.SelectAll, stringResource(R.string.select_all)) }
                            IconButton(onClick = model::clearSelection) { Icon(Icons.Outlined.Close, stringResource(R.string.clear_selection)) }
                        }
                    }
                }

                state.error?.let { ErrorNotice(it, model::refresh) }
                if (state.loading || grepLoading || imageLoading || diskUsageLoading || archiveLoading) {
                    LinearProgressIndicator(Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)))
                }
                if (state.truncated) Text(stringResource(R.string.search_limited), style = MaterialTheme.typography.bodySmall, color = tfm.warning)

                // Main Content Area
                Box(Modifier.weight(1f)) {
                    when {
                        grepResult != null -> {
                            val res = grepResult!!
                            if (res.matches.isEmpty()) {
                                EmptyPane(stringResource(R.string.no_results), query, Icons.Outlined.FindInPage)
                            } else {
                                LazyColumn(contentPadding = PaddingValues(bottom = 88.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    itemsIndexed(res.matches) { _, match ->
                                        Surface(
                                            shape = RoundedCornerShape(16.dp),
                                            color = cs.surfaceContainer,
                                            border = BorderStroke(1.dp, cs.outlineVariant),
                                            modifier = Modifier.fillMaxWidth().clickable { openEditor(match.path, match.line) }
                                        ) {
                                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    Icon(Icons.Outlined.Code, null, Modifier.size(16.dp), tint = tfm.codeTint)
                                                    Text(match.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                                    Surface(shape = RoundedCornerShape(8.dp), color = cs.primaryContainer) {
                                                        Text(":${match.line}", Modifier.padding(horizontal = 7.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = cs.onPrimaryContainer)
                                                    }
                                                }
                                                Text(match.path, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                Text(match.preview, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        state.loading && state.entries.isEmpty() -> TfmSkeletonList()
                        state.entries.isEmpty() && !state.loading && state.error == null -> EmptyPane(
                            stringResource(if (state.query.isBlank() && state.filter == "all") R.string.empty_folder else R.string.no_results),
                            if (state.query.isBlank() && state.filter == "all") stringResource(R.string.empty_folder_body) else state.query.ifBlank { state.filter }
                        )
                        options.grid -> LazyVerticalGrid(
                            GridCells.Adaptive(148.dp),
                            contentPadding = PaddingValues(bottom = 88.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(state.entries, key = { it.path }) { entry ->
                                val selected = entry.path in state.selection
                                Surface(
                                    color = if (selected) cs.primaryContainer else cs.surfaceContainer,
                                    shape = RoundedCornerShape(18.dp),
                                    border = BorderStroke(1.dp, if (selected) cs.primary else cs.outlineVariant),
                                    modifier = Modifier.combinedClickable(
                                        onClick = { if (state.selection.isNotEmpty()) model.select(entry.path) else openEntry(entry) },
                                        onLongClick = { model.select(entry.path) }
                                    )
                                ) {
                                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            FileIconBadge(entry, selected)
                                            Spacer(Modifier.weight(1f))
                                            IconButton(onClick = { menuEntry = entry }, modifier = Modifier.size(32.dp)) {
                                                Icon(Icons.Outlined.MoreVert, stringResource(R.string.more), Modifier.size(18.dp))
                                            }
                                        }
                                        Text(entry.name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            if (entry.directory) entry.mode else humanSize(entry.size),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontFamily = FontFamily.Monospace,
                                            color = cs.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            if (state.more) item(span = { GridItemSpan(maxLineSpan) }) {
                                OutlinedButton(onClick = { model.load(state.path, state.query, true, state.filter) }, enabled = !state.loading, modifier = Modifier.fillMaxWidth()) {
                                    Text(stringResource(R.string.load_more))
                                }
                            }
                        }
                        else -> LazyColumn(contentPadding = PaddingValues(bottom = 88.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(state.entries, key = { it.path }) { entry ->
                                val selected = entry.path in state.selection
                                Surface(
                                    color = if (selected) cs.primaryContainer.copy(alpha = 0.7f) else cs.surfaceContainerLow,
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(1.dp, if (selected) cs.primary else cs.outlineVariant.copy(alpha = 0.6f)),
                                    modifier = Modifier.fillMaxWidth().combinedClickable(
                                        onClick = { if (state.selection.isNotEmpty()) model.select(entry.path) else openEntry(entry) },
                                        onLongClick = { model.select(entry.path) }
                                    )
                                ) {
                                    Row(
                                        Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        FileIconBadge(entry, selected)
                                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Text(entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    if (entry.directory) entry.mode else humanSize(entry.size),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontFamily = FontFamily.Monospace,
                                                    color = cs.onSurfaceVariant
                                                )
                                                Text(
                                                    "· " + DateFormat.getDateInstance(DateFormat.SHORT).format(Date(entry.modified)),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = cs.onSurfaceVariant
                                                )
                                                if (entry.link) {
                                                    Text("→ ${entry.linkTarget}", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = tfm.symlinkTint)
                                                }
                                            }
                                        }
                                        if (entry.ext.isNotBlank() && !entry.directory) {
                                            Surface(shape = RoundedCornerShape(6.dp), color = cs.surfaceContainerHigh) {
                                                Text(entry.ext.uppercase().take(5), Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = cs.onSurfaceVariant)
                                            }
                                        }
                                        IconButton(onClick = { menuEntry = entry }, modifier = Modifier.size(34.dp)) {
                                            Icon(Icons.Outlined.MoreVert, stringResource(R.string.more), Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                            if (state.more) item {
                                OutlinedButton(onClick = { model.load(state.path, state.query, true, state.filter) }, enabled = !state.loading, modifier = Modifier.fillMaxWidth()) {
                                    Text(stringResource(R.string.load_more))
                                }
                            }
                        }
                    }

                    ExtendedFloatingActionButton(
                        onClick = { createSheet = true },
                        icon = { Icon(Icons.Outlined.Add, null) },
                        text = { Text(stringResource(R.string.create)) },
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 14.dp)
                    )
                }
            }

            if (wide && details != null) {
                Surface(color = cs.surfaceContainerLow, modifier = Modifier.width(300.dp).fillMaxHeight()) {
                    Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            IconButton(onClick = { details = null; model.clearHashes() }) { Icon(Icons.Outlined.Close, stringResource(R.string.close)) }
                        }
                        FileDetailsContent(details!!, hashes, hashesLoading, { model.loadHashes(details!!.path) }, { model.loadDiskUsage(details!!.path) })
                    }
                }
            }
        }
        if (details != null && !wide) {
            val entry = details!!
            ModalBottomSheet(onDismissRequest = { details = null; model.clearHashes() }) {
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp).padding(bottom = 24.dp)) {
                    FileDetailsContent(entry, hashes, hashesLoading, { model.loadHashes(entry.path) }, { model.loadDiskUsage(entry.path) })
                }
            }
        }
    }

    // Contextual File Action Bottom Sheet
    menuEntry?.let { entry ->
        ModalBottomSheet(onDismissRequest = { menuEntry = null }) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
                Row(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FileIconBadge(entry, false)
                    Column(Modifier.weight(1f)) {
                        Text(entry.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(if (entry.directory) entry.mode else "${humanSize(entry.size)} · ${entry.mode}", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 6.dp), color = cs.outlineVariant)
                SheetAction(Icons.Outlined.OpenInNew, stringResource(R.string.open)) { menuEntry = null; openEntry(entry) }
                if (!entry.directory) {
                    SheetAction(Icons.Outlined.Edit, stringResource(R.string.editor)) { menuEntry = null; openEditor(entry.path, 1) }
                }
                if (entry.isArchive) {
                    SheetAction(Icons.Outlined.Unarchive, stringResource(R.string.archive_inspect)) { menuEntry = null; model.inspectArchive(entry.path) }
                }
                SheetAction(Icons.Outlined.ContentCopy, stringResource(R.string.copy)) { menuEntry = null; model.copyToClipboard(listOf(entry)) }
                SheetAction(Icons.Outlined.ContentCut, stringResource(R.string.move)) { menuEntry = null; model.cutToClipboard(listOf(entry)) }
                SheetAction(Icons.Outlined.FileCopy, stringResource(R.string.duplicate)) { menuEntry = null; model.duplicate(entry) }
                SheetAction(Icons.Outlined.DriveFileRenameOutline, stringResource(R.string.rename)) { menuEntry = null; renameEntry = entry }
                SheetAction(Icons.Outlined.FolderZip, stringResource(R.string.compress)) { menuEntry = null; compressDialog = listOf(entry) }
                SheetAction(Icons.Outlined.Security, stringResource(R.string.permissions)) { menuEntry = null; permissionEntry = entry }
                SheetAction(Icons.Outlined.Info, stringResource(R.string.details)) { menuEntry = null; model.clearHashes(); details = entry }
                if (entry.directory) {
                    SheetAction(Icons.Outlined.PieChart, stringResource(R.string.disk_usage)) { menuEntry = null; model.loadDiskUsage(entry.path) }
                }
                SheetAction(Icons.Outlined.Link, stringResource(R.string.copy_path)) { clipboard.setText(AnnotatedString(entry.path)); menuEntry = null }
                SheetAction(Icons.Outlined.Terminal, stringResource(if (entry.directory) R.string.terminal else R.string.run_file)) {
                    menuEntry = null
                    openTerminal(
                        if (entry.directory) entry.path else File(entry.path).parent ?: "~/",
                        if (entry.directory) "" else runnerFor(entry.path)
                    )
                }
                SheetAction(Icons.Outlined.AutoAwesome, stringResource(R.string.ask_ai_about_file)) {
                    menuEntry = null
                    askAi("Inspect ${entry.path} and explain its structure or help me work with it.")
                }
                HorizontalDivider(Modifier.padding(vertical = 4.dp), color = cs.outlineVariant)
                SheetAction(Icons.Outlined.DeleteOutline, stringResource(R.string.delete), tint = cs.error) {
                    model.clearSelection(); model.select(entry.path); menuEntry = null; deleteDialog = true
                }
            }
        }
    }

    // Create Bottom Sheet (Files, Templates, Folder, Symlink)
    if (createSheet) {
        ModalBottomSheet(onDismissRequest = { createSheet = false }) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
                Text(stringResource(R.string.new_item), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(8.dp))
                SheetAction(Icons.AutoMirrored.Outlined.NoteAdd, stringResource(R.string.new_file)) { createSheet = false; createTemplate = ""; createDialog = "create" }
                SheetAction(Icons.Outlined.CreateNewFolder, stringResource(R.string.new_folder)) { createSheet = false; createDialog = "mkdir" }
                SheetAction(Icons.Outlined.Link, stringResource(R.string.new_symlink)) { createSheet = false; symlinkDialog = true }
                HorizontalDivider(Modifier.padding(vertical = 6.dp), color = cs.outlineVariant)
                Text(stringResource(R.string.quick_templates), style = MaterialTheme.typography.labelMedium, color = cs.primary, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                SheetAction(Icons.Outlined.Code, "Python script (.py)") {
                    createSheet = false; createTemplate = "#!/usr/bin/env python3\n\ndef main() -> None:\n    print(\"Hello from Termux!\")\n\nif __name__ == \"__main__\":\n    main()\n"
                    createDialog = "template_py"
                }
                SheetAction(Icons.Outlined.Terminal, "Bash script (.sh)") {
                    createSheet = false; createTemplate = "#!/usr/bin/env bash\nset -euo pipefail\n\necho \"Ready\"\n"
                    createDialog = "template_sh"
                }
                SheetAction(Icons.Outlined.Description, "Markdown note (.md)") {
                    createSheet = false; createTemplate = "# Title\n\n## Notes\n\n- \n"
                    createDialog = "template_md"
                }
            }
        }
    }

    // Sort Bottom Sheet
    if (sortSheet) {
        ModalBottomSheet(onDismissRequest = { sortSheet = false }) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.sort), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 6.dp))
                listOf("name" to R.string.name, "size" to R.string.size, "mtime" to R.string.modified, "ext" to R.string.sort_type).forEach { (key, label) ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { scope.launch { model.graph.settings.set("sort", key) } }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(options.sort == key, { scope.launch { model.graph.settings.set("sort", key) } })
                        Text(stringResource(label), style = MaterialTheme.typography.bodyLarge)
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 6.dp), color = cs.outlineVariant)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.descending), Modifier.weight(1f))
                    Switch(options.descending, { scope.launch { model.graph.settings.set("descending", it) } })
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.show_hidden), Modifier.weight(1f))
                    Switch(options.hidden, { scope.launch { model.graph.settings.set("hidden", it) } })
                }
            }
        }
    }

    // Image Preview Dialog (Pinch-to-Zoom!)
    imagePreview?.let { img ->
        val bitmap = remember(img.base64) {
            runCatching {
                val bytes = Base64.decode(img.base64, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }.getOrNull()
        }
        var scale by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        AlertDialog(
            onDismissRequest = model::dismissImagePreview,
            title = { Text(File(img.path).name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (bitmap != null) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(340.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.Black)
                                .pointerInput(Unit) {
                                    detectTransformGestures { _, pan, zoom, _ ->
                                        scale = (scale * zoom).coerceIn(1f, 5f)
                                        offset = if (scale > 1f) offset + pan else Offset.Zero
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = bitmap,
                                contentDescription = img.path,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize().graphicsLayer(
                                    scaleX = scale, scaleY = scale,
                                    translationX = offset.x, translationY = offset.y
                                )
                            )
                        }
                        Text("${bitmap.width}×${bitmap.height} · ${humanSize(img.size)}", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                    } else {
                        ErrorNotice("unsupported_media")
                    }
                }
            },
            confirmButton = { TextButton(onClick = model::dismissImagePreview) { Text(stringResource(R.string.close)) } }
        )
    }

    // Disk Usage Report Dialog
    diskUsage?.let { report ->
        AlertDialog(
            onDismissRequest = model::dismissDiskUsage,
            title = { Text(stringResource(R.string.disk_usage)) },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(shape = RoundedCornerShape(14.dp), color = cs.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(humanSize(report.totalBytes), style = MaterialTheme.typography.headlineSmall, color = cs.primary)
                            Text(stringResource(R.string.disk_usage_counts, report.fileCount, report.dirCount, report.linkCount), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                        }
                    }
                    report.largest.forEach { item ->
                        val frac = if (report.totalBytes > 0) (item.size.toFloat() / report.totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(item.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                                Text(humanSize(item.size), style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
                            }
                            LinearProgressIndicator(progress = { frac }, modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = model::dismissDiskUsage) { Text(stringResource(R.string.close)) } }
        )
    }

    // Archive Inspector Dialog
    archiveListing?.let { listing ->
        AlertDialog(
            onDismissRequest = model::dismissArchive,
            title = { Text(File(listing.path).name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.archive_summary, listing.total, humanSize(listing.totalSize)), style = MaterialTheme.typography.labelMedium, color = cs.primary)
                    LazyColumn(Modifier.heightIn(max = 280.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(listing.entries) { entry ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(if (entry.directory) Icons.Outlined.Folder else Icons.Outlined.Description, null, Modifier.size(16.dp), tint = cs.onSurfaceVariant)
                                Text(entry.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                                if (!entry.directory) Text(humanSize(entry.size), style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = cs.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val base = File(listing.path).nameWithoutExtension.removeSuffix(".tar")
                    val dest = (File(listing.path).parent ?: state.path).trimEnd('/') + "/" + base
                    model.extractArchive(listing.path, dest, overwrite = false)
                }) { Text(stringResource(R.string.archive_extract)) }
            },
            dismissButton = { TextButton(onClick = model::dismissArchive) { Text(stringResource(R.string.close)) } }
        )
    }

    // Compress Dialog
    compressDialog?.let { targets ->
        val defaultBase = if (targets.size == 1) targets.first().name else "archive"
        var format by remember { mutableStateOf("zip") }
        var name by remember { mutableStateOf("$defaultBase.zip") }
        AlertDialog(
            onDismissRequest = { compressDialog = null },
            title = { Text(stringResource(R.string.compress)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = format == "zip", onClick = { format = "zip"; name = "$defaultBase.zip" }, label = { Text("ZIP") })
                        FilterChip(selected = format == "tar.gz", onClick = { format = "tar.gz"; name = "$defaultBase.tar.gz" }, label = { Text("TAR.GZ") })
                    }
                    OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(enabled = name.isNotBlank(), onClick = {
                    val sources = targets.map { it.path }
                    compressDialog = null
                    model.createArchive(sources, state.path, name.trim(), format)
                }) { Text(stringResource(R.string.create)) }
            },
            dismissButton = { TextButton(onClick = { compressDialog = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    // Visual Permission Matrix Dialog
    permissionEntry?.let { entry ->
        var octal by remember(entry) { mutableStateOf(entry.mode.takeLast(3)) }
        AlertDialog(
            onDismissRequest = { permissionEntry = null },
            title = { Text("${stringResource(R.string.permissions)} · ${entry.name}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = { TfmPermissionMatrix(entry.mode) { octal = it } },
            confirmButton = {
                Button(onClick = {
                    val target = entry.path
                    permissionEntry = null
                    model.mutate("chmod", target, mapOf("mode" to octal))
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { permissionEntry = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    // Symlink Dialog
    if (symlinkDialog) {
        var linkName by remember { mutableStateOf("") }
        var targetPath by remember { mutableStateOf(state.path) }
        AlertDialog(
            onDismissRequest = { symlinkDialog = false },
            title = { Text(stringResource(R.string.new_symlink)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(linkName, { linkName = it }, label = { Text(stringResource(R.string.name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(targetPath, { targetPath = it }, label = { Text(stringResource(R.string.link_target)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(enabled = linkName.isNotBlank() && targetPath.isNotBlank(), onClick = {
                    symlinkDialog = false
                    model.createSymlink(state.path, linkName.trim(), targetPath.trim())
                }) { Text(stringResource(R.string.create)) }
            },
            dismissButton = { TextButton(onClick = { symlinkDialog = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    if (history) AlertDialog(
        onDismissRequest = { history = false },
        title = { Text(stringResource(R.string.recent_operations)) },
        text = { LazyColumn(Modifier.heightIn(max = 450.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { items(operations, key = { it.id }) { CommandCard(it) } } },
        confirmButton = { TextButton(onClick = { history = false }) { Text(stringResource(R.string.close)) } }
    )
    if (pathDialog) TextEntryDialog(stringResource(R.string.path), state.path, stringResource(R.string.path), { pathDialog = false }) { pathDialog = false; query = ""; model.load(it) }
    createDialog?.let { kind ->
        val defaultName = when (kind) { "template_py" -> "script.py"; "template_sh" -> "script.sh"; "template_md" -> "notes.md"; else -> "" }
        TextEntryDialog(stringResource(if (kind == "mkdir") R.string.new_folder else R.string.new_file), initial = defaultName, dismiss = { createDialog = null }) { name ->
            createDialog = null
            if (kind == "mkdir") model.mutate("mkdir", state.path, mapOf("name" to name))
            else model.createFileWithContent(state.path, name, createTemplate) { created -> openEditor(created, 1) }
        }
    }
    renameEntry?.let { entry -> TextEntryDialog(stringResource(R.string.rename), entry.name, dismiss = { renameEntry = null }) { renameEntry = null; model.mutate("rename", entry.path, mapOf("name" to it)) } }
    if (deleteDialog) ConfirmDialog(stringResource(R.string.delete), stringResource(R.string.delete_confirm), { deleteDialog = false }, confirmLabel = stringResource(R.string.delete), destructive = true) { deleteDialog = false; model.startBatch("delete") }
    destinationAction?.let { action -> DirectoryPicker(model, state.path, { destinationAction = null }) { destinationAction = null; model.startBatch(action, it) } }
    collision?.let { item ->
        var replacement by remember(item) { mutableStateOf(item.entry.name) }
        AlertDialog(
            onDismissRequest = { item.answer.complete(null) },
            title = { Text(stringResource(R.string.conflict)) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(stringResource(R.string.conflict_body)); OutlinedTextField(replacement, { replacement = it }, label = { Text(stringResource(R.string.name)) }) } },
            confirmButton = { Button(enabled = replacement.isNotBlank() && replacement != item.entry.name, onClick = { item.answer.complete(replacement) }) { Text(stringResource(R.string.rename)) } },
            dismissButton = { TextButton(onClick = { item.answer.complete(null) }) { Text(stringResource(R.string.skip)) } }
        )
    }
    batch?.let { progress ->
        if (collision == null) AlertDialog(
            onDismissRequest = { if (!progress.running) model.batch.value = null },
            title = { Text(stringResource(R.string.operations)) },
            text = {
                Column {
                    Text(stringResource(R.string.progress_count, progress.done, progress.total))
                    if (progress.running) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 12.dp))
                    LazyColumn(Modifier.heightIn(max = 250.dp)) {
                        items(progress.results) { (name, result) -> Text("$name · ${errorText(result)}", Modifier.padding(vertical = 4.dp)) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { if (progress.running) model.stopBatch() else model.batch.value = null }) {
                    Text(stringResource(if (progress.running) R.string.stop_remaining else R.string.close))
                }
            }
        )
    }
}

@Composable
private fun SidebarPlaceButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)) {
        Icon(icon, null, Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun SheetAction(icon: ImageVector, label: String, tint: Color = MaterialTheme.colorScheme.onSurface, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(21.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = tint)
    }
}

@Composable
private fun FileIconBadge(entry: FileEntry, selected: Boolean) {
    val tfm = LocalTfmColors.current
    val cs = MaterialTheme.colorScheme
    if (selected) {
        Surface(shape = CircleShape, color = cs.primary, modifier = Modifier.size(38.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Check, null, tint = cs.onPrimary, modifier = Modifier.size(20.dp)) }
        }
        return
    }
    val (icon, tint) = when {
        entry.link -> Icons.Outlined.Link to tfm.symlinkTint
        entry.directory -> Icons.Outlined.Folder to tfm.folderTint
        entry.category == "code" -> Icons.Outlined.Code to tfm.codeTint
        entry.category == "doc" -> Icons.Outlined.Article to tfm.docTint
        entry.category == "image" -> Icons.Outlined.Image to tfm.mediaTint
        entry.category == "audio" -> Icons.Outlined.AudioFile to tfm.mediaTint
        entry.category == "video" -> Icons.Outlined.VideoFile to tfm.mediaTint
        entry.category == "archive" -> Icons.Outlined.FolderZip to tfm.archiveTint
        else -> Icons.Outlined.Description to cs.onSurfaceVariant
    }
    Surface(shape = RoundedCornerShape(12.dp), color = tint.copy(alpha = 0.14f), modifier = Modifier.size(38.dp)) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp)) }
    }
}

@Composable
private fun FileDetailsContent(
    entry: FileEntry,
    hashes: FileHashes?,
    hashesLoading: Boolean,
    onComputeHashes: () -> Unit,
    onDiskUsage: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FileIconBadge(entry, false)
            Column(Modifier.weight(1f)) {
                Text(entry.name, style = MaterialTheme.typography.titleLarge)
                Text(entry.modeText.ifBlank { entry.mode }, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
        CodeText(entry.path, copy = true)
        Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${stringResource(R.string.size)}: ${humanSize(entry.size)} (${entry.size} B)", style = MaterialTheme.typography.bodyMedium)
                Text("${stringResource(R.string.permissions)}: ${entry.mode} (${entry.modeText})", style = MaterialTheme.typography.bodyMedium)
                Text("${stringResource(R.string.modified)}: ${DateFormat.getDateTimeInstance().format(Date(entry.modified))}", style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (entry.link) {
            Text(stringResource(R.string.link_target), style = MaterialTheme.typography.labelLarge)
            CodeText(entry.linkTarget, copy = true)
        }
        if (entry.directory) {
            OutlinedButton(onClick = onDiskUsage, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.PieChart, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.disk_usage))
            }
        } else {
            if (hashes != null) {
                Text(stringResource(R.string.checksums), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                CodeText("SHA-256: ${hashes.sha256}\nSHA-1:   ${hashes.sha1}\nMD5:     ${hashes.md5}", copy = true)
            } else {
                OutlinedButton(enabled = !hashesLoading, onClick = onComputeHashes, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.VerifiedUser, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(if (hashesLoading) R.string.loading else R.string.compute_checksums))
                }
            }
        }
    }
}

@Composable
fun DirectoryPicker(model: FilesModel, initial: String, dismiss: () -> Unit, selected: (String) -> Unit) {
    var path by remember { mutableStateOf(initial) }
    var typed by remember { mutableStateOf(initial) }
    var entries by remember { mutableStateOf<List<FileEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(path) {
        loading = true; error = null
        try {
            var offset = 0; val all = mutableListOf<FileEntry>()
            do {
                val page = model.graph.files.list(path, hidden = true, offset = offset)
                all += page.entries.filter { it.directory }
                offset = page.next
            } while (page.more)
            entries = all; typed = path
        } catch (failure: Exception) {
            error = failure.message; entries = emptyList()
        } finally {
            loading = false
        }
    }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(stringResource(R.string.choose_destination)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    typed, { typed = it },
                    label = { Text(stringResource(R.string.path)) },
                    singleLine = true,
                    trailingIcon = { IconButton(onClick = { path = typed }) { Icon(Icons.AutoMirrored.Outlined.ArrowForward, stringResource(R.string.open)) } },
                    modifier = Modifier.fillMaxWidth()
                )
                TextButton(onClick = { path = File(path).parent ?: "/" }) { Text(stringResource(R.string.up)) }
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                error?.let { ErrorNotice(it) }
                LazyColumn(Modifier.heightIn(max = 280.dp)) {
                    items(entries, key = { it.path }) { entry ->
                        ListItem(
                            headlineContent = { Text(entry.name) },
                            leadingContent = { Icon(Icons.Outlined.Folder, null, tint = LocalTfmColors.current.folderTint) },
                            modifier = Modifier.clickable { path = entry.path }
                        )
                    }
                }
            }
        },
        confirmButton = { Button(enabled = !loading && error == null && typed == path, onClick = { selected(path) }) { Text(stringResource(R.string.paste_here)) } },
        dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
