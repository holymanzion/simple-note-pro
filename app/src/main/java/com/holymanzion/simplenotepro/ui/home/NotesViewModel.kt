package com.holymanzion.simplenotepro.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.holymanzion.simplenotepro.SimpleNoteApp
import com.holymanzion.simplenotepro.data.AppSettings
import com.holymanzion.simplenotepro.data.Label
import com.holymanzion.simplenotepro.data.NoteRepository
import com.holymanzion.simplenotepro.data.NoteWithLabels
import com.holymanzion.simplenotepro.data.SettingsRepository
import com.holymanzion.simplenotepro.data.SortOrder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface NoteFilter {
    data object Notes : NoteFilter
    data object Reminders : NoteFilter
    data object Archive : NoteFilter
    data object Trash : NoteFilter
    data class ByLabel(val labelId: Long) : NoteFilter
}

data class HomeUiState(
    val loading: Boolean = true,
    val filter: NoteFilter = NoteFilter.Notes,
    val query: String = "",
    val pinned: List<NoteWithLabels> = emptyList(),
    val others: List<NoteWithLabels> = emptyList(),
    val labels: List<Label> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val selection: Set<Long> = emptySet(),
    /** Notes found only through text in their pictures, so the card can say so. */
    val imageMatches: Set<Long> = emptySet(),
) {
    val visible: List<NoteWithLabels> get() = pinned + others
    val isEmpty: Boolean get() = pinned.isEmpty() && others.isEmpty()
    val selecting: Boolean get() = selection.isNotEmpty()
    val title: String
        get() = when (filter) {
            NoteFilter.Notes -> "Notes"
            NoteFilter.Reminders -> "Reminders"
            NoteFilter.Archive -> "Archive"
            NoteFilter.Trash -> "Trash"
            is NoteFilter.ByLabel -> labels.firstOrNull { it.id == filter.labelId }?.name ?: "Label"
        }
}

/** A snackbar message, optionally undoable. */
data class HomeMessage(val text: String, val undo: (suspend () -> Unit)? = null)

class NotesViewModel(
    private val repository: NoteRepository,
    private val settingsRepository: SettingsRepository,
    private val appScope: CoroutineScope,
) : ViewModel() {

    private val filter = MutableStateFlow<NoteFilter>(NoteFilter.Notes)
    private val query = MutableStateFlow("")
    private val selection = MutableStateFlow<Set<Long>>(emptySet())

    private val messageChannel = Channel<HomeMessage>(Channel.BUFFERED)
    val messages = messageChannel.receiveAsFlow()

    private data class Inputs(val filter: NoteFilter, val query: String, val selection: Set<Long>)

    private val inputs = combine(filter, query, selection) { f, q, s -> Inputs(f, q, s) }

    val uiState: StateFlow<HomeUiState> = combine(
        repository.notes,
        repository.labels,
        settingsRepository.settings,
        inputs,
    ) { notes, labels, settings, input ->
        // A label that was deleted while being viewed falls back to all notes.
        val activeFilter = input.filter.takeUnless { f -> f is NoteFilter.ByLabel && labels.none { it.id == f.labelId } }
            ?: NoteFilter.Notes
        val matching = notes
            .filter { it.matches(activeFilter) && it.matches(input.query) }
            .sortedWith(comparatorFor(activeFilter, settings.sortOrder))
        val splitPinned = activeFilter == NoteFilter.Notes || activeFilter is NoteFilter.ByLabel
        val visibleIds = matching.map { it.note.id }.toSet()
        HomeUiState(
            loading = false,
            filter = activeFilter,
            query = input.query,
            pinned = if (splitPinned) matching.filter { it.note.isPinned } else emptyList(),
            others = if (splitPinned) matching.filterNot { it.note.isPinned } else matching,
            labels = labels,
            settings = settings,
            selection = input.selection intersect visibleIds,
            imageMatches = matching.filter { !it.matchesWrittenText(input.query) }.map { it.note.id }.toSet(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun setFilter(value: NoteFilter) {
        filter.value = value
        selection.value = emptySet()
    }

    fun setQuery(value: String) {
        query.value = value
    }

    fun toggleSelection(id: Long) {
        selection.value = selection.value.let { if (id in it) it - id else it + id }
    }

    fun selectAll() {
        selection.value = uiState.value.visible.map { it.note.id }.toSet()
    }

    fun clearSelection() {
        selection.value = emptySet()
    }

    fun toggleLayout() = settingsRepository.update { it.copy(gridLayout = !it.gridLayout) }

    fun setSort(order: SortOrder) = settingsRepository.update { it.copy(sortOrder = order) }

    // --- Bulk actions on the current selection ---

    private fun takeSelection(): List<Long> = uiState.value.selection.toList().also { clearSelection() }

    fun pinSelected() {
        val state = uiState.value
        val ids = takeSelection()
        // Pin all unless every selected note is already pinned, like Keep.
        val pin = !state.visible.filter { it.note.id in ids }.all { it.note.isPinned }
        launch { repository.setPinned(ids, pin) }
    }

    fun colorSelected(color: Int) {
        val ids = takeSelection()
        launch { repository.setColor(ids, color) }
    }

    fun archiveSelected(archive: Boolean) = archive(takeSelection(), archive)

    fun archive(ids: List<Long>, archive: Boolean) {
        if (ids.isEmpty()) return
        launch {
            repository.setArchived(ids, archive)
            val what = if (ids.size == 1) "Note" else "${ids.size} notes"
            messageChannel.send(
                HomeMessage("$what ${if (archive) "archived" else "unarchived"}") { repository.setArchived(ids, !archive) }
            )
        }
    }

    fun trashSelected() = trash(takeSelection())

    fun trash(ids: List<Long>) {
        if (ids.isEmpty()) return
        launch {
            repository.trash(ids)
            val what = if (ids.size == 1) "Note" else "${ids.size} notes"
            messageChannel.send(HomeMessage("$what moved to trash") { repository.restore(ids) })
        }
    }

    fun restoreSelected() = restore(takeSelection())

    fun restore(ids: List<Long>) {
        launch {
            repository.restore(ids)
            messageChannel.send(HomeMessage(if (ids.size == 1) "Note restored" else "${ids.size} notes restored"))
        }
    }

    fun deleteForeverSelected() = deleteForever(takeSelection())

    fun deleteForever(ids: List<Long>) {
        launch { repository.deleteForever(ids) }
    }

    fun emptyTrash() {
        launch {
            repository.emptyTrash()
            messageChannel.send(HomeMessage("Trash emptied"))
        }
    }

    fun duplicateSelected() {
        val ids = takeSelection()
        launch {
            ids.forEach { repository.duplicate(it) }
            messageChannel.send(HomeMessage(if (ids.size == 1) "Note copied" else "${ids.size} notes copied"))
        }
    }

    fun runUndo(message: HomeMessage) {
        message.undo?.let { undo -> launch { undo() } }
    }

    /** Writes go to the app scope so they complete even if the screen goes away. */
    private fun launch(block: suspend () -> Unit) {
        appScope.launch { block() }
    }

    private fun NoteWithLabels.matches(filter: NoteFilter): Boolean = when (filter) {
        NoteFilter.Notes -> !note.isTrashed && !note.isArchived
        NoteFilter.Reminders -> !note.isTrashed && note.reminderAt != null
        NoteFilter.Archive -> !note.isTrashed && note.isArchived
        NoteFilter.Trash -> note.isTrashed
        is NoteFilter.ByLabel -> !note.isTrashed && labels.any { it.id == filter.labelId }
    }

    private fun NoteWithLabels.matches(query: String): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return true
        return matchesWrittenText(q) || attachments.any { it.ocrText?.contains(q, ignoreCase = true) == true }
    }

    /** Title, body or labels, i.e. anything but text inside pictures. */
    private fun NoteWithLabels.matchesWrittenText(query: String): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return true
        return note.title.contains(q, ignoreCase = true) ||
            note.bodyText().contains(q, ignoreCase = true) ||
            labels.any { it.name.contains(q, ignoreCase = true) }
    }

    private fun comparatorFor(filter: NoteFilter, order: SortOrder): Comparator<NoteWithLabels> = when {
        filter == NoteFilter.Reminders -> compareBy { it.note.reminderAt }
        filter == NoteFilter.Trash -> compareByDescending { it.note.trashedAt }
        order == SortOrder.CREATED -> compareByDescending { it.note.createdAt }
        order == SortOrder.TITLE -> compareBy<NoteWithLabels> { it.note.title.isBlank() }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.note.title }
        else -> compareByDescending { it.note.updatedAt }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as SimpleNoteApp).container
                NotesViewModel(container.repository, container.settings, container.appScope)
            }
        }
    }
}
