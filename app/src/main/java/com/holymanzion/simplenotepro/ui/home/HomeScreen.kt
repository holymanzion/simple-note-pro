package com.holymanzion.simplenotepro.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.holymanzion.simplenotepro.data.NoteRepository
import com.holymanzion.simplenotepro.data.NoteWithLabels
import com.holymanzion.simplenotepro.data.SortOrder
import com.holymanzion.simplenotepro.ui.components.ColorPickerDialog
import com.holymanzion.simplenotepro.ui.components.ConfirmDialog
import com.holymanzion.simplenotepro.ui.components.NoteCard
import com.holymanzion.simplenotepro.ui.components.NotificationsOffBanner
import com.holymanzion.simplenotepro.ui.components.rememberNotificationsEnabled
import androidx.compose.foundation.background
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.SwipeToDismissBoxState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: NotesViewModel,
    onOpenNote: (Long) -> Unit,
    onNewNote: (checklist: Boolean, labelId: Long?) -> Unit,
    onEditLabels: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val gridState = rememberLazyStaggeredGridState()

    var showColorPicker by remember { mutableStateOf(false) }
    var confirmDeleteForever by remember { mutableStateOf(false) }
    var confirmEmptyTrash by remember { mutableStateOf(false) }
    var trashedNote by remember { mutableStateOf<NoteWithLabels?>(null) }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { message ->
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(
                message.text,
                actionLabel = if (message.undo != null) "Undo" else null,
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.runUndo(message)
        }
    }

    // Jump back to the top whenever the user switches sections.
    LaunchedEffect(state.filter) { gridState.scrollToItem(0) }

    BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }
    BackHandler(enabled = !drawerState.isOpen && state.selecting) { viewModel.clearSelection() }
    BackHandler(enabled = !drawerState.isOpen && !state.selecting && state.query.isNotEmpty()) { viewModel.setQuery("") }
    BackHandler(enabled = !drawerState.isOpen && !state.selecting && state.query.isEmpty() && state.filter != NoteFilter.Notes) {
        viewModel.setFilter(NoteFilter.Notes)
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppDrawer(
                state = state,
                onSelect = { filter ->
                    viewModel.setFilter(filter)
                    viewModel.setQuery("")
                    scope.launch { drawerState.close() }
                },
                onEditLabels = { scope.launch { drawerState.close() }; onEditLabels() },
                onOpenSettings = { scope.launch { drawerState.close() }; onOpenSettings() },
            )
        },
    ) {
        Scaffold(
            topBar = {
                if (state.selecting) {
                    SelectionTopBar(
                        state = state,
                        onClose = viewModel::clearSelection,
                        onPin = viewModel::pinSelected,
                        onColor = { showColorPicker = true },
                        onArchive = { viewModel.archiveSelected(state.filter != NoteFilter.Archive) },
                        onTrash = viewModel::trashSelected,
                        onRestore = viewModel::restoreSelected,
                        onDeleteForever = { confirmDeleteForever = true },
                        onSelectAll = viewModel::selectAll,
                        onCopy = viewModel::duplicateSelected,
                    )
                } else {
                    SearchTopBar(
                        state = state,
                        onQueryChange = viewModel::setQuery,
                        onMenu = { scope.launch { drawerState.open() } },
                        onToggleLayout = viewModel::toggleLayout,
                        onSort = viewModel::setSort,
                    )
                }
            },
            floatingActionButton = {
                val canCreate = state.filter != NoteFilter.Archive && state.filter != NoteFilter.Trash
                AnimatedVisibility(
                    visible = canCreate && !state.selecting,
                    enter = scaleIn() + fadeIn(),
                    exit = scaleOut() + fadeOut(),
                ) {
                    val labelId = (state.filter as? NoteFilter.ByLabel)?.labelId
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SmallFloatingActionButton(onClick = { onNewNote(true, labelId) }) {
                            Icon(Icons.Outlined.CheckBox, contentDescription = "New checklist")
                        }
                        ExtendedFloatingActionButton(
                            onClick = { onNewNote(false, labelId) },
                            icon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                            text = { Text("Note") },
                            expanded = !gridState.isScrollInProgress,
                        )
                    }
                }
            },
            snackbarHost = { SnackbarHost(snackbar) },
        ) { padding ->
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                if (!state.loading && state.isEmpty && state.filter != NoteFilter.Trash) {
                    EmptyState(state)
                } else {
                    NotesGrid(
                        state = state,
                        gridState = gridState,
                        onClick = { item ->
                            when {
                                state.selecting -> viewModel.toggleSelection(item.note.id)
                                item.note.isTrashed -> trashedNote = item
                                else -> onOpenNote(item.note.id)
                            }
                        },
                        onLongClick = { viewModel.toggleSelection(it.note.id) },
                        onEmptyTrash = { confirmEmptyTrash = true },
                        onSwipeArchive = { viewModel.archive(listOf(it.note.id), !it.note.isArchived) },
                        onSwipeTrash = { viewModel.trash(listOf(it.note.id)) },
                    )
                }
            }
        }
    }

    if (showColorPicker) {
        val current = state.visible.filter { it.note.id in state.selection }.map { it.note.color }.distinct().singleOrNull() ?: -1
        ColorPickerDialog(selected = current, onSelect = viewModel::colorSelected, onDismiss = { showColorPicker = false })
    }
    if (confirmDeleteForever) {
        val count = state.selection.size
        ConfirmDialog(
            title = "Delete forever?",
            message = if (count == 1) "This note will be permanently deleted." else "$count notes will be permanently deleted.",
            confirmLabel = "Delete",
            onConfirm = viewModel::deleteForeverSelected,
            onDismiss = { confirmDeleteForever = false },
        )
    }
    if (confirmEmptyTrash) {
        ConfirmDialog(
            title = "Empty trash?",
            message = "All notes in the trash will be permanently deleted.",
            confirmLabel = "Empty trash",
            onConfirm = viewModel::emptyTrash,
            onDismiss = { confirmEmptyTrash = false },
        )
    }
    trashedNote?.let { item ->
        AlertDialog(
            onDismissRequest = { trashedNote = null },
            icon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
            title = { Text("Note in trash") },
            text = { Text("Restore this note to edit it, or delete it permanently.") },
            confirmButton = {
                TextButton(onClick = { viewModel.restore(listOf(item.note.id)); trashedNote = null }) { Text("Restore") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.deleteForever(listOf(item.note.id)); trashedNote = null }) {
                    Text("Delete forever", color = MaterialTheme.colorScheme.error)
                }
            },
        )
    }
}

@Composable
private fun NotesGrid(
    state: HomeUiState,
    gridState: LazyStaggeredGridState,
    onClick: (NoteWithLabels) -> Unit,
    onLongClick: (NoteWithLabels) -> Unit,
    onEmptyTrash: () -> Unit,
    onSwipeArchive: (NoteWithLabels) -> Unit,
    onSwipeTrash: (NoteWithLabels) -> Unit,
) {
    val notificationsEnabled = rememberNotificationsEnabled()
    LazyVerticalStaggeredGrid(
        state = gridState,
        columns = if (state.settings.gridLayout) StaggeredGridCells.Adaptive(160.dp) else StaggeredGridCells.Fixed(1),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 160.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalItemSpacing = 10.dp,
        modifier = Modifier.fillMaxSize(),
    ) {
        if (state.filter != NoteFilter.Notes) {
            item(key = "title", span = StaggeredGridItemSpan.FullLine) {
                Text(
                    state.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp),
                )
            }
        }
        if (state.filter == NoteFilter.Trash) {
            item(key = "trash-info", span = StaggeredGridItemSpan.FullLine) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp)) {
                    Text(
                        "Notes in trash are deleted after ${NoteRepository.TRASH_RETENTION_DAYS} days.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onEmptyTrash, enabled = !state.isEmpty) { Text("Empty trash") }
                }
            }
            if (state.isEmpty) {
                item(key = "trash-empty", span = StaggeredGridItemSpan.FullLine) {
                    EmptyMessage(Icons.Outlined.Delete, "No notes in trash")
                }
            }
        }
        if (state.query.isNotBlank() && state.isEmpty.not()) {
            item(key = "results", span = StaggeredGridItemSpan.FullLine) {
                SectionLabel("${state.visible.size} result${if (state.visible.size == 1) "" else "s"}")
            }
        }
        if (state.filter == NoteFilter.Reminders && !notificationsEnabled &&
            state.visible.any { (it.note.reminderAt ?: 0) > System.currentTimeMillis() }
        ) {
            item(key = "notifications-off", span = StaggeredGridItemSpan.FullLine) {
                NotificationsOffBanner("Notifications are off, so these reminders won't alert you.")
            }
        }
        // Swiping is for quick triage; it stays off in the trash and while selecting.
        val swipeEnabled = state.filter != NoteFilter.Trash && !state.selecting
        if (state.pinned.isNotEmpty()) {
            item(key = "pinned-header", span = StaggeredGridItemSpan.FullLine) { SectionLabel("PINNED") }
            items(state.pinned, key = { it.note.id }) { item ->
                SwipeableNoteCard(
                    foundInImage = item.note.id in state.imageMatches,
                    item = item,
                    selected = item.note.id in state.selection,
                    swipeEnabled = swipeEnabled,
                    onClick = { onClick(item) },
                    onLongClick = { onLongClick(item) },
                    onSwipeArchive = { onSwipeArchive(item) },
                    onSwipeTrash = { onSwipeTrash(item) },
                    modifier = Modifier.animateItem(),
                )
            }
            if (state.others.isNotEmpty()) {
                item(key = "others-header", span = StaggeredGridItemSpan.FullLine) { SectionLabel("OTHERS") }
            }
        }
        items(state.others, key = { it.note.id }) { item ->
            SwipeableNoteCard(
                foundInImage = item.note.id in state.imageMatches,
                item = item,
                selected = item.note.id in state.selection,
                swipeEnabled = swipeEnabled,
                onClick = { onClick(item) },
                onLongClick = { onLongClick(item) },
                onSwipeArchive = { onSwipeArchive(item) },
                onSwipeTrash = { onSwipeTrash(item) },
                modifier = Modifier.animateItem(),
            )
        }
    }
}

/** Swipe right to archive (or unarchive), swipe left to move to trash. */
@Composable
private fun SwipeableNoteCard(
    foundInImage: Boolean,
    item: NoteWithLabels,
    selected: Boolean,
    swipeEnabled: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onSwipeArchive: () -> Unit,
    onSwipeTrash: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Deliberately `remember`, not the saveable rememberSwipeToDismissBoxState: the lazy
    // grid saves state per note id, so after "Undo" the restored card would come back in
    // its swiped-away position and be invisible. Keyed on the note's state so a card that
    // stays on screen after the action (archived while viewing a label) resets too.
    val swipeState = remember(item.note.isArchived, item.note.isTrashed) {
        SwipeToDismissBoxState(SwipeToDismissBoxValue.Settled, positionalThreshold = { distance -> distance * 0.4f })
    }
    SwipeToDismissBox(
        state = swipeState,
        modifier = modifier,
        gesturesEnabled = swipeEnabled,
        onDismiss = { direction ->
            when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> onSwipeArchive()
                SwipeToDismissBoxValue.EndToStart -> onSwipeTrash()
                SwipeToDismissBoxValue.Settled -> Unit
            }
        },
        backgroundContent = {
            val direction = swipeState.dismissDirection
            val (color, icon, alignment) = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> Triple(
                    MaterialTheme.colorScheme.primaryContainer,
                    if (item.note.isArchived) Icons.Outlined.Unarchive else Icons.Outlined.Archive,
                    Alignment.CenterStart,
                )
                SwipeToDismissBoxValue.EndToStart -> Triple(
                    MaterialTheme.colorScheme.errorContainer,
                    Icons.Outlined.Delete,
                    Alignment.CenterEnd,
                )
                SwipeToDismissBoxValue.Settled -> Triple(Color.Transparent, null, Alignment.Center)
            }
            Box(
                contentAlignment = alignment,
                modifier = Modifier
                    .fillMaxSize()
                    .background(color, RoundedCornerShape(16.dp))
                    .padding(horizontal = 20.dp),
            ) {
                if (icon != null) Icon(icon, contentDescription = null)
            }
        },
    ) {
        NoteCard(item = item, selected = selected, onClick = onClick, onLongClick = onLongClick, foundInImage = foundInImage)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 6.dp, top = 8.dp, bottom = 2.dp),
    )
}

@Composable
private fun EmptyState(state: HomeUiState) {
    if (state.query.isNotBlank()) {
        EmptyMessage(Icons.Outlined.Search, "No matching notes", "Try a different word or check another section.")
        return
    }
    when (state.filter) {
        NoteFilter.Notes -> EmptyMessage(Icons.Outlined.Lightbulb, "Notes you add appear here", "Tap “Note” to write your first one.")
        NoteFilter.Reminders -> EmptyMessage(Icons.Outlined.NotificationsNone, "Notes with upcoming reminders appear here")
        NoteFilter.Archive -> EmptyMessage(Icons.Outlined.Archive, "Your archived notes appear here")
        NoteFilter.Trash -> EmptyMessage(Icons.Outlined.Delete, "No notes in trash")
        is NoteFilter.ByLabel -> EmptyMessage(Icons.AutoMirrored.Outlined.Label, "No notes with this label yet")
    }
}

@Composable
private fun EmptyMessage(icon: ImageVector, title: String, subtitle: String? = null) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
    ) {
        Spacer(Modifier.height(96.dp))
        Icon(icon, contentDescription = null, modifier = Modifier.size(96.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (subtitle != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun SearchTopBar(
    state: HomeUiState,
    onQueryChange: (String) -> Unit,
    onMenu: () -> Unit,
    onToggleLayout: () -> Unit,
    onSort: (SortOrder) -> Unit,
) {
    var sortMenu by remember { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .fillMaxWidth()
            .height(56.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
            IconButton(onClick = onMenu) { Icon(Icons.Outlined.Menu, contentDescription = "Open menu") }
            TextField(
                value = state.query,
                onValueChange = onQueryChange,
                placeholder = {
                    Text(if (state.filter == NoteFilter.Notes) "Search your notes" else "Search ${state.title.lowercase()}")
                },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier.weight(1f),
            )
            if (state.query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) { Icon(Icons.Outlined.Close, contentDescription = "Clear search") }
            } else {
                IconButton(onClick = onToggleLayout) {
                    Icon(
                        if (state.settings.gridLayout) Icons.Outlined.ViewAgenda else Icons.Outlined.GridView,
                        contentDescription = if (state.settings.gridLayout) "List view" else "Grid view",
                    )
                }
                Box {
                    IconButton(onClick = { sortMenu = true }) {
                        Icon(Icons.AutoMirrored.Outlined.Sort, contentDescription = "Sort")
                    }
                    DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                        Text(
                            "Sort by",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                        SortOrder.entries.forEach { order ->
                            DropdownMenuItem(
                                text = { Text(order.label) },
                                leadingIcon = { RadioButton(selected = state.settings.sortOrder == order, onClick = null) },
                                onClick = { onSort(order); sortMenu = false },
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionTopBar(
    state: HomeUiState,
    onClose: () -> Unit,
    onPin: () -> Unit,
    onColor: () -> Unit,
    onArchive: () -> Unit,
    onTrash: () -> Unit,
    onRestore: () -> Unit,
    onDeleteForever: () -> Unit,
    onSelectAll: () -> Unit,
    onCopy: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val inTrash = state.filter == NoteFilter.Trash
    val inArchive = state.filter == NoteFilter.Archive
    TopAppBar(
        navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, contentDescription = "Clear selection") } },
        title = { Text("${state.selection.size}") },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        actions = {
            if (inTrash) {
                IconButton(onClick = onRestore) { Icon(Icons.Outlined.RestoreFromTrash, contentDescription = "Restore") }
                IconButton(onClick = onDeleteForever) { Icon(Icons.Outlined.DeleteForever, contentDescription = "Delete forever") }
            } else {
                if (!inArchive) IconButton(onClick = onPin) { Icon(Icons.Outlined.PushPin, contentDescription = "Pin") }
                IconButton(onClick = onColor) { Icon(Icons.Outlined.Palette, contentDescription = "Change color") }
                IconButton(onClick = onArchive) {
                    Icon(
                        if (inArchive) Icons.Outlined.Unarchive else Icons.Outlined.Archive,
                        contentDescription = if (inArchive) "Unarchive" else "Archive",
                    )
                }
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "More") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Select all") },
                        leadingIcon = { Icon(Icons.Outlined.SelectAll, contentDescription = null) },
                        onClick = { menu = false; onSelectAll() },
                    )
                    if (!inTrash) {
                        DropdownMenuItem(
                            text = { Text("Make a copy") },
                            leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) },
                            onClick = { menu = false; onCopy() },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                            onClick = { menu = false; onTrash() },
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun AppDrawer(
    state: HomeUiState,
    onSelect: (NoteFilter) -> Unit,
    onEditLabels: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    ModalDrawerSheet {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text(
                "Simple Note Pro",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 20.dp),
            )
            DrawerItem(Icons.Outlined.Lightbulb, "Notes", state.filter == NoteFilter.Notes) { onSelect(NoteFilter.Notes) }
            DrawerItem(Icons.Outlined.NotificationsNone, "Reminders", state.filter == NoteFilter.Reminders) {
                onSelect(NoteFilter.Reminders)
            }
            HorizontalDivider(Modifier.padding(horizontal = 28.dp, vertical = 8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 28.dp, end = 16.dp),
            ) {
                Text(
                    "Labels",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onEditLabels) { Text("Edit") }
            }
            state.labels.forEach { label ->
                DrawerItem(
                    Icons.AutoMirrored.Outlined.Label,
                    label.name,
                    (state.filter as? NoteFilter.ByLabel)?.labelId == label.id,
                ) { onSelect(NoteFilter.ByLabel(label.id)) }
            }
            DrawerItem(Icons.Outlined.Add, "Create new label", false, onEditLabels)
            HorizontalDivider(Modifier.padding(horizontal = 28.dp, vertical = 8.dp))
            DrawerItem(Icons.Outlined.Archive, "Archive", state.filter == NoteFilter.Archive) { onSelect(NoteFilter.Archive) }
            DrawerItem(Icons.Outlined.Delete, "Trash", state.filter == NoteFilter.Trash) { onSelect(NoteFilter.Trash) }
            HorizontalDivider(Modifier.padding(horizontal = 28.dp, vertical = 8.dp))
            DrawerItem(Icons.Outlined.Settings, "Settings", false, onOpenSettings)
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DrawerItem(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    NavigationDrawerItem(
        icon = { Icon(icon, contentDescription = null) },
        label = { Text(label) },
        selected = selected,
        onClick = onClick,
        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
    )
}
