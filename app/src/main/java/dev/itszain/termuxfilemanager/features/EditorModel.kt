package dev.itszain.termuxfilemanager.features

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.itszain.termuxfilemanager.AppGraph
import dev.itszain.termuxfilemanager.core.FileContent
import dev.itszain.termuxfilemanager.core.FileFailure
import dev.itszain.termuxfilemanager.data.AppSettings
import dev.itszain.termuxfilemanager.data.DraftRecord
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

data class EditorState(
    val path: String = "",
    val text: String = "",
    val original: String = "",
    val fingerprint: String = "",
    val loading: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
    val restored: Boolean = false,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val largeMode: Boolean = false,
    val startLine: Int = 1,
    val endLine: Int = 0,
    val totalLines: Int = 0,
    val fileSize: Long = 0L,
    val hasMoreLines: Boolean = false
) {
    val dirty: Boolean get() = !largeMode && text != original
    val lineEnding: String get() = if (text.contains("\r\n")) "CRLF" else "LF"
}

class EditorModel(val graph: AppGraph) : ViewModel() {
    val state = MutableStateFlow(EditorState())
    val settings = graph.settings.appearance.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private val undo = ArrayDeque<String>()
    private val redo = ArrayDeque<String>()
    private var lastUndoPushAt = 0L
    private var draftJob: Job? = null
    private var openJob: Job? = null

    fun open(path: String, discard: Boolean = false) {
        openJob?.cancel()
        state.value = EditorState(path, loading = true)
        openJob = viewModelScope.launch {
            try {
                val content = graph.files.read(path)
                draftJob?.join()
                if (discard) graph.dao.deleteDraft(path)
                val draft = graph.dao.draft(path)
                undo.clear()
                redo.clear()
                lastUndoPushAt = 0L
                val activeText = draft?.text ?: content.text
                state.value = EditorState(
                    path = path,
                    text = activeText,
                    original = content.text,
                    fingerprint = draft?.fingerprint ?: content.fingerprint,
                    restored = draft != null,
                    error = if (draft != null && draft.fingerprint != content.fingerprint) "changed" else null,
                    fileSize = activeText.toByteArray(Charsets.UTF_8).size.toLong(),
                    totalLines = activeText.count { it == '\n' } + 1
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (tooLarge: FileFailure) {
                if (tooLarge.code == "too_large") {
                    loadLargeRange(path, 1)
                } else {
                    state.update { it.copy(loading = false, error = tooLarge.code) }
                }
            } catch (error: Exception) {
                state.update { it.copy(loading = false, error = error.message ?: "io") }
            }
        }
    }

    fun loadLargeRange(path: String = state.value.path, startLine: Int = 1) {
        openJob?.cancel()
        state.update { it.copy(path = path, loading = true, error = null) }
        openJob = viewModelScope.launch {
            try {
                val range = graph.files.readRange(path, startLine.coerceAtLeast(1), 200)
                state.value = EditorState(
                    path = path,
                    text = range.text,
                    original = range.text,
                    loading = false,
                    largeMode = true,
                    startLine = range.startLine,
                    endLine = range.endLine,
                    totalLines = range.totalLines,
                    fileSize = range.size,
                    hasMoreLines = range.more
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                state.update { it.copy(loading = false, error = error.message ?: "io") }
            }
        }
    }

    fun edit(text: String) {
        val current = state.value
        if (text == current.text || current.saving || current.loading || current.largeMode) return
        val now = System.currentTimeMillis()
        val delta = abs(text.length - current.text.length)
        val boundary = text.endsWith('\n') || delta > 12 || now - lastUndoPushAt > 900L || undo.isEmpty()
        if (boundary) {
            undo.addLast(current.text)
            if (undo.size > 150) undo.removeFirst()
            lastUndoPushAt = now
        }
        redo.clear()
        state.update {
            it.copy(
                text = text,
                canUndo = undo.isNotEmpty(),
                canRedo = false,
                fileSize = text.toByteArray(Charsets.UTF_8).size.toLong(),
                totalLines = text.count { c -> c == '\n' } + 1
            )
        }
        persistDraft()
    }

    fun undo() {
        val current = state.value
        if (!current.saving && !current.loading && !current.largeMode && undo.isNotEmpty()) {
            redo.addLast(current.text)
            val prev = undo.removeLast()
            lastUndoPushAt = 0L
            state.update {
                it.copy(
                    text = prev,
                    canUndo = undo.isNotEmpty(),
                    canRedo = true,
                    fileSize = prev.toByteArray(Charsets.UTF_8).size.toLong(),
                    totalLines = prev.count { c -> c == '\n' } + 1
                )
            }
            persistDraft()
        }
    }

    fun redo() {
        val current = state.value
        if (!current.saving && !current.loading && !current.largeMode && redo.isNotEmpty()) {
            undo.addLast(current.text)
            val next = redo.removeLast()
            lastUndoPushAt = 0L
            state.update {
                it.copy(
                    text = next,
                    canUndo = true,
                    canRedo = redo.isNotEmpty(),
                    fileSize = next.toByteArray(Charsets.UTF_8).size.toLong(),
                    totalLines = next.count { c -> c == '\n' } + 1
                )
            }
            persistDraft()
        }
    }

    fun replaceAll(query: String, replacement: String, caseSensitive: Boolean, useRegex: Boolean): Int {
        val current = state.value
        if (query.isEmpty() || current.largeMode) return 0
        val options = if (caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
        val regex = runCatching { Regex(if (useRegex) query else Regex.escape(query), options) }.getOrNull() ?: return 0
        val count = regex.findAll(current.text).count()
        if (count == 0) return 0
        val updated = if (useRegex) regex.replace(current.text, replacement) else regex.replace(current.text) { replacement }
        lastUndoPushAt = 0L
        edit(updated)
        return count
    }

    fun formatJson(): String? {
        val raw = state.value.text.trim()
        if (raw.isEmpty()) return "Empty file"
        return try {
            val formatted = if (raw.startsWith("[")) JSONArray(raw).toString(2) else JSONObject(raw).toString(2)
            lastUndoPushAt = 0L
            edit(formatted + "\n")
            null
        } catch (error: Exception) {
            error.message ?: "Invalid JSON"
        }
    }

    fun buildUnifiedDiff(): String {
        val oldLines = state.value.original.lines()
        val newLines = state.value.text.lines()
        if (oldLines == newLines) return ""
        val sb = StringBuilder()
        val max = maxOf(oldLines.size, newLines.size)
        var changes = 0
        for (i in 0 until max) {
            val o = oldLines.getOrNull(i)
            val n = newLines.getOrNull(i)
            if (o != n) {
                changes++
                if (o != null) sb.append("-").append(i + 1).append(" | ").append(o).append('\n')
                if (n != null) sb.append("+").append(i + 1).append(" | ").append(n).append('\n')
                if (changes >= 200) {
                    sb.append("…\n")
                    break
                }
            }
        }
        return sb.toString().trimEnd()
    }

    fun persistDraft() {
        val previous = draftJob
        previous?.cancel()
        val current = state.value
        if (current.fingerprint.isBlank() || current.largeMode) return
        draftJob = graph.scope.launch {
            previous?.join()
            graph.dao.saveDraft(DraftRecord(current.path, current.text, current.fingerprint))
        }
    }

    fun discard(done: () -> Unit) {
        viewModelScope.launch {
            draftJob?.cancelAndJoin()
            graph.dao.deleteDraft(state.value.path)
            state.update { it.copy(text = it.original) }
            done()
        }
    }

    fun save(force: Boolean = false, done: () -> Unit = {}) {
        val current = state.value
        if (current.saving || current.largeMode || current.fingerprint.isBlank()) return
        state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                draftJob?.join()
                val saved = graph.files.write(FileContent(current.path, current.text, current.fingerprint), force = force)
                graph.dao.deleteDraft(current.path)
                state.update {
                    it.copy(
                        saving = false,
                        original = saved.text,
                        fingerprint = saved.fingerprint,
                        restored = false,
                        error = null
                    )
                }
                done()
            } catch (error: Exception) {
                state.update { it.copy(saving = false, error = error.message ?: "io") }
            }
        }
    }

    override fun onCleared() {
        if (state.value.dirty) persistDraft()
        super.onCleared()
    }
}
