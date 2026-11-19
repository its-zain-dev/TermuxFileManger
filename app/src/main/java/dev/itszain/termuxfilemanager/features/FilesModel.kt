package dev.itszain.termuxfilemanager.features

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.itszain.termuxfilemanager.AppGraph
import dev.itszain.termuxfilemanager.BackgroundWorkCoordinator
import dev.itszain.termuxfilemanager.R
import dev.itszain.termuxfilemanager.core.*
import dev.itszain.termuxfilemanager.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.util.UUID

data class FileTab(val id: String = UUID.randomUUID().toString(), val path: String = "~/") {
    val label: String get() = when {
        path == "~" || path == "~/" || path == "/data/data/com.termux/files/home" -> "~"
        path == "/" -> "/"
        else -> File(path).name.ifBlank { path }
    }
}

data class FileClipboard(val action: String, val entries: List<FileEntry>)

data class FilesState(
    val path: String = "~/",
    val entries: List<FileEntry> = emptyList(),
    val query: String = "",
    val filter: String = "all",
    val loading: Boolean = false,
    val error: String? = null,
    val next: Int = 0,
    val more: Boolean = false,
    val total: Int = 0,
    val truncated: Boolean = false,
    val selection: Set<String> = emptySet(),
    val gitBranch: String = "",
    val diskTotal: Long = 0L,
    val diskFree: Long = 0L,
    val diskUsed: Long = 0L
)

data class BatchProgress(
    val done: Int,
    val total: Int,
    val results: List<Pair<String, String>> = emptyList(),
    val running: Boolean = true
)

data class Collision(val entry: FileEntry, val answer: CompletableDeferred<String?>)

class FilesModel(val graph: AppGraph) : ViewModel() {
    val state = MutableStateFlow(FilesState())
    val tabs = MutableStateFlow(listOf(FileTab()))
    val activeTabIndex = MutableStateFlow(0)
    val clipboard = MutableStateFlow<FileClipboard?>(null)
    val grepResult = MutableStateFlow<GrepResult?>(null)
    val grepLoading = MutableStateFlow(false)
    val imagePreview = MutableStateFlow<ImagePreviewData?>(null)
    val imageLoading = MutableStateFlow(false)
    val hashes = MutableStateFlow<FileHashes?>(null)
    val hashesLoading = MutableStateFlow(false)
    val diskUsage = MutableStateFlow<DiskUsageReport?>(null)
    val diskUsageLoading = MutableStateFlow(false)
    val archiveListing = MutableStateFlow<ArchiveListing?>(null)
    val archiveLoading = MutableStateFlow(false)
    val notice = MutableStateFlow<String?>(null)

    val settings = graph.settings.appearance.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    val places = graph.dao.places().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val operations = graph.dao.commands("files").stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val batch = MutableStateFlow<BatchProgress?>(null)
    val collision = MutableStateFlow<Collision?>(null)

    private var generation = 0
    private var listing: Job? = null
    @Volatile private var cancelBatch = false

    fun load(
        path: String = state.value.path,
        query: String = "",
        append: Boolean = false,
        filter: String = state.value.filter
    ) {
        val request = ++generation
        listing?.cancel()
        grepResult.value = null
        val old = state.value
        state.value = if (append) {
            old.copy(loading = true, error = null)
        } else {
            FilesState(
                path = path,
                query = query,
                filter = filter,
                loading = true,
                gitBranch = old.gitBranch,
                diskTotal = old.diskTotal,
                diskFree = old.diskFree,
                diskUsed = old.diskUsed
            )
        }
        listing = viewModelScope.launch {
            try {
                val options = settings.value
                val page = graph.files.list(
                    path = path,
                    query = query,
                    hidden = options.hidden,
                    sort = options.sort,
                    descending = options.descending,
                    offset = if (append) old.next else 0,
                    filter = filter
                )
                if (generation != request) return@launch
                state.value = state.value.copy(
                    path = page.path,
                    entries = (if (append) old.entries else emptyList()) + page.entries,
                    loading = false,
                    next = page.next,
                    more = page.more,
                    total = page.total,
                    truncated = page.truncated,
                    gitBranch = page.gitBranch,
                    diskTotal = if (page.diskTotal > 0) page.diskTotal else state.value.diskTotal,
                    diskFree = if (page.diskTotal > 0) page.diskFree else state.value.diskFree,
                    diskUsed = if (page.diskTotal > 0) page.diskUsed else state.value.diskUsed
                )
                syncActiveTabPath(page.path)
                if (query.isBlank()) {
                    graph.dao.savePlace(PlaceRecord(page.path, graph.dao.place(page.path)?.favorite ?: false, System.currentTimeMillis()))
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (request == generation) state.update { it.copy(loading = false, error = error.message ?: "io") }
            }
        }
    }

    fun setFilter(filter: String) {
        if (state.value.filter != filter) load(state.value.path, state.value.query, false, filter)
    }

    fun searchContent(query: String, caseSensitive: Boolean = false, regex: Boolean = false) {
        if (query.isBlank()) { grepResult.value = null; return }
        viewModelScope.launch {
            grepLoading.value = true
            state.update { it.copy(error = null) }
            try {
                grepResult.value = graph.files.grep(state.value.path, query, settings.value.hidden, caseSensitive, regex)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                state.update { it.copy(error = error.message ?: "io") }
            } finally {
                grepLoading.value = false
            }
        }
    }

    fun clearGrep() { grepResult.value = null }

    fun addTab(path: String = state.value.path) {
        if (tabs.value.size >= 6) return
        val next = tabs.value + FileTab(path = path)
        tabs.value = next
        activeTabIndex.value = next.lastIndex
        load(path)
    }

    fun selectTab(index: Int) {
        val list = tabs.value
        if (index !in list.indices) return
        activeTabIndex.value = index
        load(list[index].path)
    }

    fun closeTab(index: Int) {
        val list = tabs.value
        if (list.size <= 1 || index !in list.indices) return
        val updated = list.filterIndexed { i, _ -> i != index }
        tabs.value = updated
        val nextIndex = activeTabIndex.value.coerceAtMost(updated.lastIndex)
        activeTabIndex.value = nextIndex
        load(updated[nextIndex].path)
    }

    private fun syncActiveTabPath(resolvedPath: String) {
        val idx = activeTabIndex.value
        val list = tabs.value
        if (idx in list.indices && list[idx].path != resolvedPath) {
            tabs.value = list.mapIndexed { i, t -> if (i == idx) t.copy(path = resolvedPath) else t }
        }
    }

    fun refresh() = load(state.value.path, state.value.query, false, state.value.filter)

    fun parent() {
        if (grepResult.value != null) { grepResult.value = null; return }
        if (state.value.query.isNotBlank()) load() else load(File(state.value.path).parent ?: "~/")
    }

    fun select(path: String) {
        state.update { it.copy(selection = if (path in it.selection) it.selection - path else it.selection + path) }
    }

    fun clearSelection() { state.update { it.copy(selection = emptySet()) } }
    fun selectAll() { state.update { it.copy(selection = it.entries.map(FileEntry::path).toSet()) } }

    fun copyToClipboard(entries: List<FileEntry> = selectedEntries()) {
        if (entries.isEmpty()) return
        clipboard.value = FileClipboard("copy", entries)
        clearSelection()
    }

    fun cutToClipboard(entries: List<FileEntry> = selectedEntries()) {
        if (entries.isEmpty()) return
        clipboard.value = FileClipboard("move", entries)
        clearSelection()
    }

    fun clearClipboard() { clipboard.value = null }

    fun pasteClipboard(destination: String = state.value.path) {
        val clip = clipboard.value ?: return
        clipboard.value = null
        startBatchEntries(clip.action, clip.entries, destination)
    }

    fun selectedEntries(): List<FileEntry> = state.value.entries.filter { it.path in state.value.selection }

    fun favorite(path: String) {
        viewModelScope.launch {
            val current = graph.dao.place(path)
            graph.dao.savePlace(PlaceRecord(path, !(current?.favorite ?: false), current?.visited ?: 0))
        }
    }

    fun mutate(action: String, path: String, args: Map<String, String>, done: () -> Unit = {}) {
        graph.scope.launch {
            try {
                graph.files.action(action, path, args)
                withContext(Dispatchers.Main) { done(); refresh() }
            } catch (error: Exception) {
                state.update { it.copy(error = error.message ?: "io") }
            }
        }
    }

    fun createFileWithContent(parentDir: String, name: String, initialText: String, openAfter: ((String) -> Unit)? = null) {
        graph.scope.launch {
            try {
                val created = graph.files.action("create", parentDir, mapOf("name" to name))
                if (initialText.isNotEmpty()) {
                    val current = graph.files.read(created)
                    graph.files.write(current.copy(text = initialText))
                }
                withContext(Dispatchers.Main) {
                    refresh()
                    openAfter?.invoke(created)
                }
            } catch (error: Exception) {
                state.update { it.copy(error = error.message ?: "io") }
            }
        }
    }

    fun createSymlink(parentDir: String, name: String, target: String) {
        mutate("symlink", parentDir, mapOf("name" to name, "target" to target))
    }

    fun duplicate(entry: FileEntry) {
        val parent = File(entry.path).parent ?: state.value.path
        val dot = entry.name.lastIndexOf('.')
        val copyName = if (!entry.directory && dot > 0) {
            "${entry.name.substring(0, dot)}_copy${entry.name.substring(dot)}"
        } else {
            "${entry.name}_copy"
        }
        mutate("copy", entry.path, mapOf("destDir" to parent, "name" to copyName))
    }

    fun loadImagePreview(path: String) {
        viewModelScope.launch {
            imageLoading.value = true
            imagePreview.value = null
            try {
                imagePreview.value = graph.files.readBase64(path)
            } catch (error: Exception) {
                state.update { it.copy(error = error.message ?: "io") }
            } finally {
                imageLoading.value = false
            }
        }
    }

    fun dismissImagePreview() { imagePreview.value = null; imageLoading.value = false }

    fun loadHashes(path: String) {
        viewModelScope.launch {
            hashesLoading.value = true
            hashes.value = null
            try {
                hashes.value = graph.files.hash(path)
            } catch (error: Exception) {
                state.update { it.copy(error = error.message ?: "io") }
            } finally {
                hashesLoading.value = false
            }
        }
    }

    fun clearHashes() { hashes.value = null; hashesLoading.value = false }

    fun loadDiskUsage(path: String) {
        viewModelScope.launch {
            diskUsageLoading.value = true
            diskUsage.value = null
            try {
                diskUsage.value = graph.files.diskUsage(path)
            } catch (error: Exception) {
                state.update { it.copy(error = error.message ?: "io") }
            } finally {
                diskUsageLoading.value = false
            }
        }
    }

    fun dismissDiskUsage() { diskUsage.value = null; diskUsageLoading.value = false }

    fun inspectArchive(path: String) {
        viewModelScope.launch {
            archiveLoading.value = true
            archiveListing.value = null
            try {
                archiveListing.value = graph.files.archiveList(path)
            } catch (error: Exception) {
                state.update { it.copy(error = error.message ?: "io") }
            } finally {
                archiveLoading.value = false
            }
        }
    }

    fun dismissArchive() { archiveListing.value = null; archiveLoading.value = false }

    fun createArchive(sources: List<String>, destDir: String, name: String, format: String) {
        graph.scope.launch {
            BackgroundWorkCoordinator.beginTask(
                graph.context, "archive-create", "files",
                graph.context.getString(R.string.archive_create), name
            )
            try {
                graph.files.archiveCreate(sources, destDir, name, format)
                withContext(Dispatchers.Main) { clearSelection(); refresh(); notice.value = "saved" }
            } catch (error: Exception) {
                state.update { it.copy(error = error.message ?: "io") }
            } finally {
                BackgroundWorkCoordinator.finishTask(graph.context, "archive-create")
            }
        }
    }

    fun extractArchive(path: String, destDir: String, overwrite: Boolean = false) {
        graph.scope.launch {
            BackgroundWorkCoordinator.beginTask(
                graph.context, "archive-extract", "files",
                graph.context.getString(R.string.archive_extract), File(path).name
            )
            try {
                graph.files.archiveExtract(path, destDir, overwrite)
                withContext(Dispatchers.Main) { dismissArchive(); refresh(); notice.value = "complete" }
            } catch (error: Exception) {
                state.update { it.copy(error = error.message ?: "io") }
            } finally {
                BackgroundWorkCoordinator.finishTask(graph.context, "archive-extract")
            }
        }
    }

    fun startBatch(action: String, destination: String = "") {
        startBatchEntries(action, selectedEntries(), destination)
    }

    private fun startBatchEntries(action: String, selected: List<FileEntry>, destination: String) {
        if (batch.value?.running == true || selected.isEmpty()) return
        // Selecting a directory and one of its children must not execute the child twice.
        val entries = selected.filter { item ->
            selected.none { it.directory && it.path != item.path && item.path.startsWith(it.path.trimEnd('/') + "/") }
        }
        cancelBatch = false
        batch.value = BatchProgress(0, entries.size)
        val taskId = "files-batch-${System.currentTimeMillis()}"
        BackgroundWorkCoordinator.beginTask(
            graph.context, taskId, "files",
            graph.context.getString(R.string.operations),
            graph.context.getString(R.string.progress_count, 0, entries.size),
            progress = 0, max = entries.size
        )
        graph.scope.launch {
            try {
                val results = mutableListOf<Pair<String, String>>()
                for (entry in entries) {
                    if (cancelBatch) break
                    var name = entry.name
                    while (!cancelBatch) {
                        try {
                            graph.files.action(action, entry.path, mapOf("destDir" to destination, "name" to name))
                            results += entry.name to "complete"
                            break
                        } catch (error: FileFailure) {
                            if (error.code == "exists" && action != "delete") {
                                val answer = CompletableDeferred<String?>()
                                collision.value = Collision(entry, answer)
                                val replacement = answer.await()
                                collision.value = null
                                if (replacement == null) { results += entry.name to "skipped"; break }
                                name = replacement
                            } else {
                                results += entry.name to error.code
                                break
                            }
                        } catch (error: Exception) {
                            results += entry.name to (if (error is TimeoutCancellationException) "command_pending" else error.message ?: "io")
                            if (error is TimeoutCancellationException) cancelBatch = true
                            break
                        }
                    }
                    batch.value = BatchProgress(results.size, entries.size, results.toList())
                    BackgroundWorkCoordinator.updateTask(
                        graph.context, taskId,
                        graph.context.getString(R.string.progress_count, results.size, entries.size),
                        progress = results.size, max = entries.size
                    )
                }
                batch.value = BatchProgress(results.size, entries.size, results.toList(), false)
                withContext(Dispatchers.Main) { clearSelection(); refresh() }
            } finally {
                BackgroundWorkCoordinator.finishTask(graph.context, taskId)
            }
        }
    }

    fun stopBatch() {
        cancelBatch = true
        collision.value?.answer?.complete(null)
    }
}
