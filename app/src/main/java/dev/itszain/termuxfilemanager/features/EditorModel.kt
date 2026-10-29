package dev.itszain.termuxfilemanager.features

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.itszain.termuxfilemanager.AppGraph
import dev.itszain.termuxfilemanager.core.FileContent
import dev.itszain.termuxfilemanager.data.DraftRecord
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class EditorState(val path: String = "", val text: String = "", val original: String = "", val fingerprint: String = "",
    val loading: Boolean = false, val saving: Boolean = false, val error: String? = null,
    val restored: Boolean = false, val canUndo: Boolean = false, val canRedo: Boolean = false) {
    val dirty get() = text != original
}
class EditorModel(private val graph: AppGraph) : ViewModel() {
    val state = MutableStateFlow(EditorState())
    private val undo = ArrayDeque<String>()
    private val redo = ArrayDeque<String>()
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
                undo.clear(); redo.clear()
                state.value = EditorState(path, draft?.text ?: content.text, content.text, draft?.fingerprint ?: content.fingerprint,
                    restored = draft != null, error = if (draft != null && draft.fingerprint != content.fingerprint) "changed" else null)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { state.update { it.copy(loading = false, error = error.message ?: "io") } }
        }
    }
    fun edit(text: String) {
        if (text == state.value.text || state.value.saving || state.value.loading) return
        undo.addLast(state.value.text); if (undo.size > 40) undo.removeFirst()
        redo.clear()
        state.update { it.copy(text = text, canUndo = true, canRedo = false) }
        persistDraft()
    }
    fun undo() { if (!state.value.saving && !state.value.loading && undo.isNotEmpty()) { redo.addLast(state.value.text); state.update { it.copy(text = undo.removeLast(), canUndo = undo.isNotEmpty(), canRedo = true) }; persistDraft() } }
    fun redo() { if (!state.value.saving && !state.value.loading && redo.isNotEmpty()) { undo.addLast(state.value.text); state.update { it.copy(text = redo.removeLast(), canUndo = true, canRedo = redo.isNotEmpty()) }; persistDraft() } }
    fun persistDraft() {
        val previous = draftJob
        previous?.cancel()
        val current = state.value
        if (current.fingerprint.isBlank()) return
        draftJob = graph.scope.launch { previous?.join(); graph.dao.saveDraft(DraftRecord(current.path, current.text, current.fingerprint)) }
    }
    fun discard(done: () -> Unit) {
        viewModelScope.launch { draftJob?.cancelAndJoin(); graph.dao.deleteDraft(state.value.path); state.update { it.copy(text = it.original) }; done() }
    }
    fun save(done: () -> Unit = {}) {
        if (state.value.saving) return
        val current = state.value
        if (current.fingerprint.isBlank()) return
        state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                draftJob?.join()
                val saved = graph.files.write(FileContent(current.path, current.text, current.fingerprint))
                graph.dao.deleteDraft(current.path)
                state.update { it.copy(saving = false, original = saved.text, fingerprint = saved.fingerprint, restored = false) }
                done()
            } catch (error: Exception) { state.update { it.copy(saving = false, error = error.message ?: "io") } }
        }
    }
    override fun onCleared() { if (state.value.dirty) persistDraft(); super.onCleared() }
}
