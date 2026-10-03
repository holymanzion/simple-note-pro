package com.holymanzion.simplenotepro.ui.editor

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import android.net.Uri
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.material.icons.outlined.AddBox
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import com.holymanzion.simplenotepro.data.Attachment
import com.holymanzion.simplenotepro.text.RichText
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.zIndex
import com.holymanzion.simplenotepro.ui.components.NotificationsOffBanner
import com.holymanzion.simplenotepro.ui.components.rememberNotificationsEnabled
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddAlert
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.RemoveDone
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.holymanzion.simplenotepro.data.ChecklistItem
import com.holymanzion.simplenotepro.ui.components.ColorPickerRow
import com.holymanzion.simplenotepro.ui.components.InfoRow
import com.holymanzion.simplenotepro.ui.components.LabelPickerDialog
import com.holymanzion.simplenotepro.ui.components.MiniChip
import com.holymanzion.simplenotepro.ui.components.ReminderDialog
import com.holymanzion.simplenotepro.ui.components.friendlyTime
import com.holymanzion.simplenotepro.ui.components.fullDateTime
import com.holymanzion.simplenotepro.ui.components.reminderLabel
import com.holymanzion.simplenotepro.ui.theme.noteBackground

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditorScreen(
    viewModel: EditorViewModel,
    onBack: () -> Unit,
    onOpenNote: (Long) -> Unit,
) {
    val context = LocalContext.current
    val labels by viewModel.allLabels.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val background = noteBackground(viewModel.color, MaterialTheme.colorScheme.surface)

    var showColors by rememberSaveable { mutableStateOf(false) }
    var showReminder by rememberSaveable { mutableStateOf(false) }
    var showLabels by rememberSaveable { mutableStateOf(false) }
    var showInfo by rememberSaveable { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showAddMenu by remember { mutableStateOf(false) }
    var viewingImage by remember { mutableStateOf<Attachment?>(null) }
    val attachments by viewModel.attachments.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(viewModel.message) {
        viewModel.message?.let { snackbar.showSnackbar(it); viewModel.message = null }
    }

    // The body's text + cursor, hoisted so the formatting toolbar can act on the selection.
    // Kept in step with the view model like PlainField does (undo moves the cursor to the end).
    var contentFieldState by remember { mutableStateOf(TextFieldValue(viewModel.content, TextRange(viewModel.content.length))) }
    val contentField = if (contentFieldState.text == viewModel.content) contentFieldState
    else TextFieldValue(viewModel.content, TextRange(viewModel.content.length))
    SideEffect { if (contentFieldState != contentField) contentFieldState = contentField }
    val updateContent = { value: TextFieldValue ->
        contentFieldState = value
        if (value.text != viewModel.content) viewModel.content = value.text
    }
    var contentFocused by remember { mutableStateOf(false) }

    val pickImages = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_IMAGES_PER_PICK)) { uris ->
        viewModel.addImages(uris)
    }
    var pendingPhoto by rememberSaveable { mutableStateOf<Uri?>(null) }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        pendingPhoto?.let { if (saved) viewModel.addImages(listOf(it)) }
        pendingPhoto = null
    }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    BackHandler(onBack = onBack)

    Scaffold(
        containerColor = background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = background),
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = viewModel::togglePinned) {
                        Icon(
                            if (viewModel.pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                            contentDescription = if (viewModel.pinned) "Unpin" else "Pin",
                        )
                    }
                    IconButton(onClick = { showReminder = true }) {
                        Icon(
                            if (viewModel.reminderAt != null) Icons.Outlined.NotificationsActive else Icons.Outlined.AddAlert,
                            contentDescription = "Reminder",
                        )
                    }
                    IconButton(onClick = { viewModel.toggleArchived(); onBack() }) {
                        Icon(
                            if (viewModel.archived) Icons.Outlined.Unarchive else Icons.Outlined.Archive,
                            contentDescription = if (viewModel.archived) "Unarchive" else "Archive",
                        )
                    }
                },
            )
        },
        bottomBar = {
            Column(
                Modifier
                    .navigationBarsPadding()
                    .imePadding(),
            ) {
                if (showColors) {
                    ColorPickerRow(
                        selected = viewModel.color,
                        onSelect = viewModel::changeColor,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
                if (contentFocused && !viewModel.isChecklist) {
                    FormattingToolbar(onAction = { transform -> updateContent(transform(contentField)) })
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                ) {
                    IconButton(onClick = { showColors = !showColors }) {
                        Icon(Icons.Outlined.Palette, contentDescription = "Color")
                    }
                    Box {
                        IconButton(onClick = { showAddMenu = true }) {
                            Icon(Icons.Outlined.AddBox, contentDescription = "Add")
                        }
                        DropdownMenu(expanded = showAddMenu, onDismissRequest = { showAddMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Take photo") },
                                leadingIcon = { Icon(Icons.Outlined.PhotoCamera, contentDescription = null) },
                                onClick = {
                                    showAddMenu = false
                                    val uri = cameraOutputUri(context)
                                    pendingPhoto = uri
                                    runCatching { takePhoto.launch(uri) }
                                        .onFailure { viewModel.message = "No camera app available" }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Add image") },
                                leadingIcon = { Icon(Icons.Outlined.Image, contentDescription = null) },
                                onClick = {
                                    showAddMenu = false
                                    pickImages.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(if (viewModel.isChecklist) "Hide checkboxes" else "Show checkboxes") },
                                leadingIcon = {
                                    Icon(
                                        if (viewModel.isChecklist) Icons.AutoMirrored.Outlined.Notes else Icons.Outlined.CheckBox,
                                        contentDescription = null,
                                    )
                                },
                                onClick = {
                                    showAddMenu = false
                                    if (viewModel.isChecklist) viewModel.convertToText() else viewModel.convertToChecklist()
                                },
                            )
                        }
                    }
                    IconButton(onClick = { showLabels = true }) {
                        Icon(Icons.AutoMirrored.Outlined.Label, contentDescription = "Labels")
                    }
                    Text(
                        text = if (viewModel.isNew) "New note" else "Edited ${friendlyTime(viewModel.updatedAt)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                    )
                    IconButton(onClick = viewModel::undo, enabled = viewModel.canUndo) {
                        Icon(Icons.AutoMirrored.Outlined.Undo, contentDescription = "Undo")
                    }
                    IconButton(onClick = viewModel::redo, enabled = viewModel.canRedo) {
                        Icon(Icons.AutoMirrored.Outlined.Redo, contentDescription = "Redo")
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "More") }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Share") },
                                leadingIcon = { Icon(Icons.Outlined.Share, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    val send = Intent(Intent.ACTION_SEND)
                                        .setType("text/plain")
                                        .putExtra(Intent.EXTRA_SUBJECT, viewModel.title)
                                        .putExtra(Intent.EXTRA_TEXT, viewModel.shareText())
                                    context.startActivity(Intent.createChooser(send, "Share note"))
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Make a copy") },
                                leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) },
                                onClick = { showMenu = false; viewModel.makeCopy(onOpenNote) },
                            )
                            if (viewModel.isChecklist) {
                                DropdownMenuItem(
                                    text = { Text("Uncheck all items") },
                                    leadingIcon = { Icon(Icons.Outlined.RemoveDone, contentDescription = null) },
                                    onClick = { showMenu = false; viewModel.uncheckAll() },
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete checked items") },
                                    leadingIcon = { Icon(Icons.Outlined.DeleteSweep, contentDescription = null) },
                                    onClick = { showMenu = false; viewModel.deleteCheckedItems() },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Note info") },
                                leadingIcon = { Icon(Icons.Outlined.Info, contentDescription = null) },
                                onClick = { showMenu = false; showInfo = true },
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Delete") },
                                leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                                onClick = { showMenu = false; viewModel.moveToTrash(); onBack() },
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        if (!viewModel.loaded) return@Scaffold
        val titleFocus = remember { FocusRequester() }
        val contentFocus = remember { FocusRequester() }
        LaunchedEffect(Unit) {
            if (viewModel.isNew && !viewModel.isChecklist) contentFocus.requestFocus()
        }

        val listState = rememberLazyListState()
        val dragState = remember { ChecklistDragState(listState, viewModel::moveItem) }
        val haptics = LocalHapticFeedback.current
        val notificationsEnabled = rememberNotificationsEnabled()

        // Pictures sit below the writing, so one being added to a long note would land out
        // of view. While adding, scroll to them (the "Adding image…" row lives there too).
        // Only then: opening a note that already has pictures keeps you at the top.
        val imagesIndex = if (!viewModel.isChecklist) {
            2 // title, body
        } else {
            val activeCount = if (settings.checkedToBottom) viewModel.items.count { !it.checked } else viewModel.items.size
            val hasCheckedSection = settings.checkedToBottom && viewModel.items.any { it.checked }
            2 + activeCount + (if (hasCheckedSection) 1 else 0) // title, items, "List item" row, checked section
        }
        val currentImagesIndex by rememberUpdatedState(imagesIndex)
        val addingImages = viewModel.importingImages > 0
        LaunchedEffect(addingImages) {
            if (addingImages) listState.animateScrollToItem(currentImagesIndex)
        }

        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(bottom = 24.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            item(key = "title") {
                PlainField(
                    value = viewModel.title,
                    onValueChange = { value ->
                        if ('\n' in value) {
                            // Enter in the title moves on to the body, like most note apps.
                            viewModel.title = value.replace("\n", "")
                            if (!viewModel.isChecklist) contentFocus.requestFocus()
                            else viewModel.focusItemId = viewModel.items.firstOrNull()?.id
                                ?: run { viewModel.addItem(); viewModel.focusItemId }
                        } else {
                            viewModel.title = value
                        }
                    },
                    placeholder = "Title",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                        .focusRequester(titleFocus)
                        .testTag("editor-title"),
                )
            }

            if (viewModel.isChecklist) {
                val (active, done) = if (settings.checkedToBottom) {
                    viewModel.items.partition { !it.checked }
                } else {
                    viewModel.items.toList() to emptyList()
                }
                dragState.targets = active.map { it.id }
                itemsIndexed(active, key = { _, item -> item.id }) { index, item ->
                    val dragging = dragState.draggedId == item.id
                    ChecklistRow(
                        item = item,
                        viewModel = viewModel,
                        modifier = Modifier
                            .zIndex(if (dragging) 1f else 0f)
                            .graphicsLayer { translationY = dragState.translationOf(item.id) }
                            .then(if (dragging) Modifier else Modifier.animateItem()),
                        dragHandle = Modifier.pointerInput(item.id) {
                            detectVerticalDragGestures(
                                onDragStart = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    dragState.start(item.id)
                                },
                                onDragEnd = dragState::end,
                                onDragCancel = dragState::end,
                                onVerticalDrag = { change, dy ->
                                    change.consume()
                                    dragState.drag(dy)
                                },
                            )
                        },
                        onMoveUp = active.getOrNull(index - 1)?.let { above -> { viewModel.moveItem(item.id, above.id) } },
                        onMoveDown = active.getOrNull(index + 1)?.let { below -> { viewModel.moveItem(item.id, below.id) } },
                    )
                }
                item(key = "add-item") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.addItem(afterId = active.lastOrNull()?.id) }
                            .padding(start = 40.dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "List item",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 14.dp),
                        )
                    }
                }
                if (done.isNotEmpty()) {
                    item(key = "checked-header") {
                        var expanded by rememberSaveable { mutableStateOf(true) }
                        Column {
                            HorizontalDivider(Modifier.padding(horizontal = 20.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { expanded = !expanded }
                                    .padding(horizontal = 20.dp, vertical = 10.dp),
                            ) {
                                Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null)
                                Text(
                                    "${done.size} checked item${if (done.size == 1) "" else "s"}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 12.dp),
                                )
                            }
                            if (expanded) done.forEach { ChecklistRow(it, viewModel) }
                        }
                    }
                }
            } else {
                item(key = "content") {
                    RichContentField(
                        value = contentField,
                        onValueChange = { new -> updateContent(RichText.continueList(contentField, new) ?: new) },
                        modifier = Modifier
                            .padding(horizontal = 20.dp)
                            // A tall empty body is an easy place to tap and type, but it would
                            // push pictures far down, so shrink it once there are some.
                            .heightIn(min = if (attachments.isEmpty()) 240.dp else 48.dp)
                            .focusRequester(contentFocus)
                            .onFocusChanged { contentFocused = it.isFocused }
                            .testTag("editor-content"),
                    )
                }
            }

            // Pictures follow the writing, like attachments at the end of a document.
            if (attachments.isNotEmpty() || viewModel.importingImages > 0) {
                item(key = "images") {
                    EditorImages(
                        attachments,
                        importing = viewModel.importingImages,
                        onOpen = { viewingImage = it },
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }

            val reminder = viewModel.reminderAt
            val attached = labels.filter { it.id in viewModel.labelIds }
            val links = if (viewModel.isChecklist) emptyList() else RichText.links(viewModel.content)
            if (reminder != null || attached.isNotEmpty() || links.isNotEmpty()) {
                item(key = "chips") {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                    ) {
                        if (reminder != null) {
                            MiniChip(
                                if (viewModel.reminderRepeat != null) Icons.Outlined.Repeat else Icons.Outlined.Alarm,
                                reminderLabel(reminder, viewModel.reminderRepeat),
                                strike = reminder < System.currentTimeMillis() && viewModel.reminderRepeat == null,
                                onClick = { showReminder = true },
                            )
                        }
                        attached.forEach { MiniChip(Icons.AutoMirrored.Outlined.Label, it.name, onClick = { showLabels = true }) }
                        // Links can't be tapped inside the text while editing, so offer them here.
                        links.forEach { url ->
                            MiniChip(Icons.Outlined.Link, Uri.parse(url).host ?: url, onClick = { context.openLink(url) })
                        }
                    }
                }
            }
            if (reminder != null && reminder > System.currentTimeMillis() && !notificationsEnabled) {
                item(key = "notifications-off") {
                    NotificationsOffBanner(
                        "Notifications are off, so this reminder won't alert you.",
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
        }
    }

    if (showReminder) {
        ReminderDialog(
            current = viewModel.reminderAt,
            currentRepeat = viewModel.reminderRepeat,
            onSet = { at, repeat -> viewModel.changeReminder(at, repeat); ensureNotificationPermission() },
            onRemove = { viewModel.changeReminder(null) },
            onDismiss = { showReminder = false },
        )
    }
    if (showLabels) {
        LabelPickerDialog(
            labels = labels,
            selected = viewModel.labelIds,
            onToggle = viewModel::toggleLabel,
            onCreate = viewModel::createAndAttachLabel,
            onDismiss = { showLabels = false },
        )
    }
    viewingImage?.let { opened ->
        // The live row, so text read while the viewer is open shows up.
        val image = attachments.firstOrNull { it.id == opened.id } ?: opened
        ImageViewer(
            attachment = image,
            onDelete = { viewModel.removeImage(image); viewingImage = null },
            onAddText = viewModel::addTextFromImage,
            onDismiss = { viewingImage = null },
        )
    }
    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            title = { Text("Note info") },
            text = {
                Column {
                    InfoRow("Created", fullDateTime(viewModel.createdAt))
                    InfoRow("Last edited", fullDateTime(viewModel.updatedAt))
                    InfoRow("Words", viewModel.wordCount.toString())
                    InfoRow("Characters", viewModel.charCount.toString())
                    if (viewModel.isChecklist) {
                        InfoRow("Items", "${viewModel.items.count { it.checked }} of ${viewModel.items.size} done")
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showInfo = false }) { Text("Close") } },
        )
    }
}

/**
 * Drag-to-reorder for checklist rows in a LazyColumn.
 *
 * The dragged row's translation is derived from the finger position and the row's
 * current layout slot, so a swap needs no manual offset bookkeeping: once the list
 * relayouts, the translation shrinks by exactly the distance the slot moved.
 */
private class ChecklistDragState(
    private val listState: LazyListState,
    private val onMove: (id: Long, targetId: Long) -> Unit,
) {
    var draggedId by mutableStateOf<Long?>(null)
        private set
    private var startOffset = 0
    private var totalDelta by mutableFloatStateOf(0f)
    private var offsetAtLastMove: Int? = null

    /** Rows the dragged item may swap with; refreshed on every composition. */
    var targets: List<Long> = emptyList()

    private fun layoutOf(id: Long) = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == id }

    fun start(id: Long) {
        val slot = layoutOf(id) ?: return
        draggedId = id
        startOffset = slot.offset
        totalDelta = 0f
        offsetAtLastMove = null
    }

    fun drag(dy: Float) {
        val id = draggedId ?: return
        totalDelta += dy
        val slot = layoutOf(id) ?: return
        // Drag events arrive faster than frames; wait until the last swap has been laid
        // out, or the stale layout would make the row swap straight back.
        if (offsetAtLastMove == slot.offset) return
        offsetAtLastMove = null
        val fingerMiddle = startOffset + totalDelta + slot.size / 2f
        val target = listState.layoutInfo.visibleItemsInfo.firstOrNull { other ->
            val key = other.key as? Long
            key != null && key != id && key in targets &&
                fingerMiddle > other.offset && fingerMiddle < other.offset + other.size
        } ?: return
        offsetAtLastMove = slot.offset
        onMove(id, target.key as Long)
    }

    /** Vertical shift that keeps the dragged row under the finger. */
    fun translationOf(id: Long): Float {
        if (id != draggedId) return 0f
        val slot = layoutOf(id) ?: return 0f
        return startOffset + totalDelta - slot.offset
    }

    fun end() {
        draggedId = null
        totalDelta = 0f
        offsetAtLastMove = null
    }
}

@Composable
private fun ChecklistRow(
    item: ChecklistItem,
    viewModel: EditorViewModel,
    modifier: Modifier = Modifier,
    dragHandle: Modifier? = null,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
) {
    val focus = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(viewModel.focusItemId) {
        if (viewModel.focusItemId == item.id) {
            focus.requestFocus()
            viewModel.focusItemId = null
        }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(end = 4.dp),
    ) {
        if (dragHandle != null) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(width = HANDLE_WIDTH, height = 48.dp)
                    .then(dragHandle)
                    .semantics {
                        contentDescription = "Drag to reorder"
                        // TalkBack users can't drag, so offer the same move as actions.
                        customActions = listOfNotNull(
                            onMoveUp?.let { move -> CustomAccessibilityAction("Move up") { move(); true } },
                            onMoveDown?.let { move -> CustomAccessibilityAction("Move down") { move(); true } },
                        )
                    },
            ) {
                Icon(
                    Icons.Outlined.DragIndicator,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        // Rows without a handle keep its width so every checkbox lines up.
        else Spacer(Modifier.width(HANDLE_WIDTH))
        Checkbox(checked = item.checked, onCheckedChange = { viewModel.toggleItem(item.id) })
        PlainField(
            value = item.text,
            onValueChange = { viewModel.updateItemText(item.id, it) },
            placeholder = "",
            singleLine = false,
            style = MaterialTheme.typography.bodyLarge.copy(
                textDecoration = if (item.checked) TextDecoration.LineThrough else null,
                color = if (item.checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            ),
            modifier = Modifier
                .weight(1f)
                .focusRequester(focus)
                .testTag("checklist-item")
                .onFocusChanged { focused = it.isFocused },
        )
        IconButton(
            onClick = { viewModel.removeItem(item.id) },
            modifier = Modifier.size(40.dp),
            enabled = focused,
        ) {
            if (focused) Icon(Icons.Outlined.Close, contentDescription = "Remove item", modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun PlainField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
) {
    val color = if (style.color == Color.Unspecified) MaterialTheme.colorScheme.onSurface else style.color
    // Own the cursor so that text replaced from outside (undo/redo, checklist
    // conversion) puts the cursor at the end instead of wherever it happened to be.
    var fieldState by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    val fieldValue = if (fieldState.text == value) fieldState else TextFieldValue(value, TextRange(value.length))
    SideEffect { if (fieldState != fieldValue) fieldState = fieldValue }
    BasicTextField(
        value = fieldValue,
        onValueChange = { new ->
            fieldState = new
            if (new.text != value) onValueChange(new.text)
        },
        singleLine = singleLine,
        textStyle = style.copy(color = color),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty() && placeholder.isNotEmpty()) {
                    Text(placeholder, style = style.copy(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)))
                }
                inner()
            }
        },
    )
}

private val HANDLE_WIDTH = 28.dp
