package dev.itszain.termuxfilemanager.features

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.itszain.termuxfilemanager.AppGraph
import dev.itszain.termuxfilemanager.core.*
import dev.itszain.termuxfilemanager.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File

data class FilesState(val path: String = "~/", val entries: List<FileEntry> = emptyList(), val query: String = "",
    val loading: Boolean = false, val error: String? = null, val next: Int = 0, val more: Boolean = false,
    val total: Int = 0, val truncated: Boolean = false, val selection: Set<String> = emptySet())
data class BatchProgress(val done: Int, val total: Int, val results: List<Pair<String, String>> = emptyList(), val running: Boolean = true)
data class Collision(val entry: FileEntry, val answer: CompletableDeferred<String?>)

class FilesModel(val graph: AppGraph) : ViewModel() {
    val state = MutableStateFlow(FilesState())
    val settings = graph.settings.appearance.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    val places = graph.dao.places().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val operations = graph.dao.commands("files").stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val batch = MutableStateFlow<BatchProgress?>(null)
    val collision = MutableStateFlow<Collision?>(null)
    private var generation = 0
    private var listing: Job? = null
    @Volatile private var cancelBatch = false
    fun load(path: String = state.value.path, query: String = "", append: Boolean = false) {
        val request = ++generation
        listing?.cancel()
        val old = state.value
        state.value = if (append) old.copy(loading = true, error = null) else FilesState(path, query = query, loading = true)
        listing = viewModelScope.launch {
            try {
                val options = settings.value
                val page = graph.files.list(path, query, options.hidden, options.sort, options.descending, if (append) old.next else 0)
                if (generation != request) return@launch
                state.value = state.value.copy(path = page.path, entries = (if (append) old.entries else emptyList()) + page.entries,
                    loading = false, next = page.next, more = page.more, total = page.total, truncated = page.truncated)
                if (query.isBlank()) graph.dao.savePlace(PlaceRecord(page.path, graph.dao.place(page.path)?.favorite ?: false, System.currentTimeMillis()))
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { if (request == generation) state.update { it.copy(loading = false, error = error.message ?: "io") } }
        }
    }
    fun refresh() = load(state.value.path, state.value.query)
    fun parent() { if (state.value.query.isNotBlank()) load() else load(File(state.value.path).parent ?: "~/") }
    fun select(path: String) { state.update { it.copy(selection = if (path in it.selection) it.selection - path else it.selection + path) } }
    fun clearSelection() { state.update { it.copy(selection = emptySet()) } }
    fun selectAll() { state.update { it.copy(selection = it.entries.map(FileEntry::path).toSet()) } }
    fun favorite(path: String) { viewModelScope.launch { val current = graph.dao.place(path); graph.dao.savePlace(PlaceRecord(path, !(current?.favorite ?: false), current?.visited ?: 0)) } }
    fun mutate(action: String, path: String, args: Map<String, String>, done: () -> Unit = {}) {
        graph.scope.launch {
            try { graph.files.action(action, path, args); withContext(Dispatchers.Main) { done(); refresh() } }
            catch (error: Exception) { state.update { it.copy(error = error.message ?: "io") } }
        }
    }
    fun startBatch(action: String, destination: String = "") {
        if (batch.value?.running == true) return
        val selected = state.value.entries.filter { it.path in state.value.selection }
        // Selecting a directory and one of its children must not execute the child twice.
        val entries = selected.filter { item -> selected.none { it.directory && it.path != item.path && item.path.startsWith(it.path.trimEnd('/') + "/") } }
        cancelBatch = false
        batch.value = BatchProgress(0, entries.size)
        graph.scope.launch {
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
                        } else { results += entry.name to error.code; break }
                    } catch (error: Exception) {
                        results += entry.name to (if (error is TimeoutCancellationException) "command_pending" else error.message ?: "io")
                        if (error is TimeoutCancellationException) cancelBatch = true
                        break
                    }
                }
                batch.value = BatchProgress(results.size, entries.size, results.toList())
            }
            batch.value = BatchProgress(results.size, entries.size, results.toList(), false)
            withContext(Dispatchers.Main) { clearSelection(); refresh() }
        }
    }
    fun stopBatch() { cancelBatch = true; collision.value?.answer?.complete(null) }
}
