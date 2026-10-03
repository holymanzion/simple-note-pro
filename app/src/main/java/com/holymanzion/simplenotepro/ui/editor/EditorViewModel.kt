package com.holymanzion.simplenotepro.ui.editor

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.holymanzion.simplenotepro.SimpleNoteApp
import com.holymanzion.simplenotepro.data.AppSettings
import com.holymanzion.simplenotepro.data.Attachment
import com.holymanzion.simplenotepro.data.ChecklistCodec
import com.holymanzion.simplenotepro.data.ChecklistItem
import com.holymanzion.simplenotepro.data.Label
import com.holymanzion.simplenotepro.data.Note
import com.holymanzion.simplenotepro.data.NoteRepository
import com.holymanzion.simplenotepro.data.Repeat
import com.holymanzion.simplenotepro.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** The part of a note that undo/redo steps through. */
private data class EditState(
    val title: String,
    val content: String,
    val items: List<ChecklistItem>,
    val isChecklist: Boolean,
)

/** Everything that, when changed, makes the note "edited". */
private data class Snapshot(
    val title: String,
    val content: String,
    val items: List<ChecklistItem>,
    val isChecklist: Boolean,
    val color: Int,
    val pinned: Boolean,
    val archived: Boolean,
    val trashed: Boolean,
    val reminderAt: Long?,
    val reminderRepeat: Repeat?,
    val labelIds: Set<Long>,
)

@OptIn(FlowPreview::class)
class EditorViewModel(
    private val savedState: SavedStateHandle,
    private val repository: NoteRepository,
    settingsRepository: SettingsRepository,
    private val appScope: CoroutineScope,
) : ViewModel() {

    /** Observable, so the footer switches from "New note" to "Edited …" after the first save. */
    private var noteId by mutableLongStateOf(savedState.get<Long>(ARG_NOTE_ID)?.takeIf { it > 0 } ?: 0L)

    var loaded by mutableStateOf(false)
        private set
    var title by mutableStateOf("")
    var content by mutableStateOf("")
    val items = mutableStateListOf<ChecklistItem>()
    var isChecklist by mutableStateOf(savedState.get<Boolean>(ARG_CHECKLIST) ?: false)
        private set
    var color by mutableIntStateOf(0)
        private set
    var pinned by mutableStateOf(false)
        private set
    var archived by mutableStateOf(false)
        private set
    var trashed by mutableStateOf(false)
        private set
    var reminderAt by mutableStateOf<Long?>(null)
        private set
    var reminderRepeat by mutableStateOf<Repeat?>(null)
        private set
    var labelIds by mutableStateOf(emptySet<Long>())
        private set
    var createdAt by mutableLongStateOf(System.currentTimeMillis())
        private set
    var updatedAt by mutableLongStateOf(System.currentTimeMillis())
        private set

    /** Checklist item that should take focus next (after Enter or "Add item"). */
    var focusItemId by mutableStateOf<Long?>(null)

    val allLabels: StateFlow<List<Label>> =
        repository.labels.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val settings: StateFlow<AppSettings> = settingsRepository.settings

    val isNew: Boolean get() = noteId == 0L

    private val saveLock = Mutex()
    private var lastSaved: Snapshot? = null
    private var trashedAt: Long? = null

    // Declared before init: a new note records its first undo step without suspending.
    private val history = mutableStateListOf<EditState>()
    private var historyIndex by mutableIntStateOf(0)

    init {
        viewModelScope.launch {
            val existing = if (noteId > 0) repository.get(noteId) else null
            if (existing != null) {
                val n = existing.note
                title = n.title
                content = n.content
                items.addAll(n.items)
                isChecklist = n.isChecklist
                color = n.color
                pinned = n.isPinned
                archived = n.isArchived
                trashed = n.isTrashed
                trashedAt = n.trashedAt
                reminderAt = n.reminderAt
                reminderRepeat = n.repeat
                labelIds = existing.labels.map { it.id }.toSet()
                createdAt = n.createdAt
                updatedAt = n.updatedAt
            } else {
                noteId = 0
                savedState.get<Long>(ARG_LABEL_ID)?.takeIf { it > 0 }?.let { labelIds = setOf(it) }
                if (isChecklist) addItem()
            }
            lastSaved = snapshot()
            history.add(editState())
            loaded = true

            // A pause in typing closes one undo step, so undo reverts a burst of
            // typing rather than a single character.
            launch {
                snapshotFlow { editState() }
                    .debounce(UNDO_PAUSE_MS)
                    .collect { commitHistory() }
            }

            // Autosave shortly after the user stops typing.
            snapshotFlow { snapshot() }
                .drop(1)
                .debounce(600)
                .collect { saveInBackground() }
        }
    }

    // --- Undo / redo ---
    //
    // History covers what the user writes (title, body, checklist items and the
    // text/checklist switch). Color, pin, labels and reminders apply immediately
    // and have their own undo-free controls, as in most note apps.

    private fun editState() = EditState(title, content, items.toList(), isChecklist)

    /** Records the current text as a new undo step if it differs from the step we're on. */
    private fun commitHistory() {
        if (!loaded) return
        val current = editState()
        if (current == history.getOrNull(historyIndex)) return
        while (history.lastIndex > historyIndex) history.removeAt(history.lastIndex)
        history.add(current)
        if (history.size > MAX_HISTORY) history.removeAt(0)
        historyIndex = history.lastIndex
    }

    val canUndo: Boolean get() = loaded && (historyIndex > 0 || editState() != history.getOrNull(historyIndex))
    val canRedo: Boolean get() = loaded && historyIndex < history.lastIndex && editState() == history[historyIndex]

    fun undo() {
        commitHistory() // keep any typing that hasn't been recorded yet, so redo can restore it
        if (historyIndex == 0) return
        historyIndex--
        apply(history[historyIndex])
    }

    fun redo() {
        if (!canRedo) return
        historyIndex++
        apply(history[historyIndex])
    }

    private fun apply(state: EditState) {
        title = state.title
        content = state.content
        items.clear()
        items.addAll(state.items)
        isChecklist = state.isChecklist
    }

    private fun snapshot() = Snapshot(
        title, content, items.toList(), isChecklist, color, pinned, archived, trashed, reminderAt, reminderRepeat, labelIds,
    )

    private fun saveInBackground() {
        appScope.launch { save() }
    }

    /** [force] creates the row even for a blank note, which images need to attach to. */
    private suspend fun save(force: Boolean = false) = saveLock.withLock {
        if (!loaded) return@withLock
        val snap = snapshot()
        if (snap == lastSaved && !(force && noteId == 0L)) return@withLock
        val now = System.currentTimeMillis()
        val note = buildNote(snap, now)
        // Never create a row for a note that has nothing in it yet.
        if (noteId == 0L && note.isBlank() && !force) return@withLock
        noteId = repository.save(note, snap.labelIds)
        // If the process is killed, the restored editor reopens this row instead of a duplicate.
        savedState[ARG_NOTE_ID] = noteId
        updatedAt = now
        lastSaved = snap
    }

    private fun buildNote(s: Snapshot, now: Long) = Note(
        id = noteId,
        title = s.title,
        content = if (s.isChecklist) "" else s.content,
        checklist = if (s.isChecklist) ChecklistCodec.encode(s.items) else "",
        isChecklist = s.isChecklist,
        color = s.color,
        isPinned = s.pinned,
        isArchived = s.archived,
        isTrashed = s.trashed,
        trashedAt = if (s.trashed) trashedAt ?: now else null,
        reminderAt = s.reminderAt,
        reminderRepeat = if (s.reminderAt == null) null else s.reminderRepeat?.name,
        createdAt = createdAt,
        updatedAt = now,
    )

    /** Current note as plain text, for sharing and copying. */
    fun shareText(): String = buildString {
        if (title.isNotBlank()) append(title.trim()).append("\n\n")
        append(
            if (isChecklist) items.joinToString("\n") { (if (it.checked) "☑ " else "☐ ") + it.text }
            else content
        )
    }.trim()

    val wordCount: Int
        get() = (title + " " + if (isChecklist) ChecklistCodec.toText(items) else content)
            .split(Regex("\\s+")).count { it.isNotBlank() }

    val charCount: Int
        get() = title.length + if (isChecklist) items.sumOf { it.text.length } else content.length

    // --- Note-level actions ---

    fun togglePinned() {
        pinned = !pinned
        if (pinned) archived = false
    }

    fun changeColor(index: Int) {
        color = index
    }

    fun changeReminder(at: Long?, repeat: Repeat? = null) {
        reminderAt = at
        reminderRepeat = if (at == null) null else repeat
    }

    // --- Images ---

    /** The note's images; empty until the note has been saved once. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val attachments: StateFlow<List<Attachment>> = snapshotFlow { noteId }
        .flatMapLatest { id -> if (id == 0L) flowOf(emptyList()) else repository.observeAttachments(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Images still being copied in, for a progress indicator. */
    var importingImages by mutableIntStateOf(0)
        private set

    /** One-off message for the editor to show (e.g. an image that couldn't be read). */
    var message by mutableStateOf<String?>(null)

    fun addImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        importingImages += uris.size
        appScope.launch {
            save(force = true)
            var failed = 0
            uris.forEach { uri ->
                runCatching { repository.addImage(noteId, uri) }.onFailure { failed++ }
                withContext(Dispatchers.Main) { importingImages-- }
            }
            if (failed > 0) {
                withContext(Dispatchers.Main) {
                    message = if (failed == 1) "Couldn't add that image" else "Couldn't add $failed images"
                }
            }
            // Make the new pictures' text searchable.
            repository.readPendingImageText()
        }
    }

    /** Adds text read from a picture: after the body, or as one checklist item per line. */
    fun addTextFromImage(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        if (isChecklist) {
            clean.lines().filter { it.isNotBlank() }.forEach { line ->
                items.add(ChecklistItem(ChecklistCodec.newId(), line.trim()))
            }
        } else {
            content = if (content.isBlank()) clean else content.trimEnd() + "\n\n" + clean
        }
    }

    fun removeImage(attachment: Attachment) {
        appScope.launch { repository.removeAttachment(attachment) }
    }

    fun toggleLabel(id: Long) {
        labelIds = if (id in labelIds) labelIds - id else labelIds + id
    }

    fun createAndAttachLabel(name: String) {
        viewModelScope.launch {
            repository.createLabel(name)?.let { labelIds = labelIds + it }
        }
    }

    /** Archive toggles and the caller leaves the editor. */
    fun toggleArchived() {
        archived = !archived
        if (archived) pinned = false
        saveInBackground()
    }

    fun moveToTrash() {
        trashed = true
        trashedAt = System.currentTimeMillis()
        pinned = false
        saveInBackground()
    }

    fun restoreFromTrash() {
        trashed = false
        trashedAt = null
    }

    fun makeCopy(onCopied: (Long) -> Unit) {
        appScope.launch {
            save()
            val source = noteId.takeIf { it > 0 } ?: return@launch
            val copyId = repository.duplicate(source) ?: return@launch
            viewModelScope.launch { onCopied(copyId) }
        }
    }

    fun convertToChecklist() {
        if (isChecklist) return
        items.clear()
        items.addAll(ChecklistCodec.fromText(content))
        if (items.isEmpty()) addItem()
        content = ""
        isChecklist = true
    }

    fun convertToText() {
        if (!isChecklist) return
        content = ChecklistCodec.toText(items.filter { it.text.isNotBlank() })
        items.clear()
        isChecklist = false
    }

    // --- Checklist editing ---

    fun addItem(afterId: Long? = null, text: String = "") {
        val item = ChecklistItem(ChecklistCodec.newId(), text)
        val index = afterId?.let { id -> items.indexOfFirst { it.id == id } }?.takeIf { it >= 0 }
        if (index == null) items.add(item) else items.add(index + 1, item)
        focusItemId = item.id
    }

    /**
     * Pressing Enter inside an item splits it into a new item below; pasting several
     * lines creates one item per line.
     */
    fun updateItemText(id: Long, text: String) {
        val index = items.indexOfFirst { it.id == id }
        if (index < 0) return
        if ('\n' !in text) {
            items[index] = items[index].copy(text = text)
            return
        }
        val lines = text.split('\n')
        items[index] = items[index].copy(text = lines.first())
        // Keep the trailing empty line (plain Enter) but drop blank lines from pastes.
        val rest = lines.drop(1).filterIndexed { i, line -> line.isNotBlank() || i == lines.lastIndex - 1 }
        var afterId = id
        rest.forEach { line ->
            addItem(afterId = afterId, text = line)
            afterId = focusItemId ?: afterId
        }
    }

    fun toggleItem(id: Long) {
        val index = items.indexOfFirst { it.id == id }
        if (index >= 0) items[index] = items[index].copy(checked = !items[index].checked)
    }

    fun removeItem(id: Long) {
        val index = items.indexOfFirst { it.id == id }
        if (index < 0) return
        items.removeAt(index)
        focusItemId = items.getOrNull(index - 1)?.id
    }

    /**
     * Moves item [id] into the slot of item [targetId]: after it when moving down, before it
     * when moving up. Works on ids, not indices, because the screen may hide checked items
     * between two visible neighbours.
     */
    fun moveItem(id: Long, targetId: Long) {
        val from = items.indexOfFirst { it.id == id }
        val to = items.indexOfFirst { it.id == targetId }
        if (from < 0 || to < 0 || from == to) return
        items.add(to, items.removeAt(from))
    }

    fun uncheckAll() {
        items.replaceAll { it.copy(checked = false) }
    }

    fun deleteCheckedItems() {
        items.removeAll { it.checked }
    }

    override fun onCleared() {
        // Final save once the screen is gone; an emptied existing note is discarded.
        appScope.launch {
            save()
            val id = noteId
            // A note with only a picture isn't empty.
            if (id > 0 && buildNote(snapshot(), updatedAt).isBlank() && repository.get(id)?.attachments.isNullOrEmpty()) {
                repository.deleteForever(listOf(id))
            }
        }
    }

    companion object {
        const val ARG_NOTE_ID = "noteId"
        const val ARG_CHECKLIST = "checklist"
        const val ARG_LABEL_ID = "labelId"
        private const val UNDO_PAUSE_MS = 500L
        private const val MAX_HISTORY = 100

        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as SimpleNoteApp).container
                EditorViewModel(createSavedStateHandle(), container.repository, container.settings, container.appScope)
            }
        }
    }
}
