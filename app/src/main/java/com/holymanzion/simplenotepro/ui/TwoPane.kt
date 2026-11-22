package com.holymanzion.simplenotepro.ui

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import com.holymanzion.simplenotepro.ui.editor.EditorScreen
import com.holymanzion.simplenotepro.ui.editor.EditorViewModel

/** Whether the notes list and the open note sit side by side. */
object TwoPane {
    /** Lets device tests try the tablet layout on a phone. */
    var forced: Boolean? by mutableStateOf(null)

    /** Tablets, foldables (open) and Chromebooks; not phones turned sideways. */
    fun isWide(widthDp: Int, heightDp: Int): Boolean = widthDp >= 720 && heightDp >= 480
}

@Composable
fun useTwoPane(): Boolean {
    val config = LocalConfiguration.current
    return TwoPane.forced ?: TwoPane.isWide(config.screenWidthDp, config.screenHeightDp)
}

/** What the right-hand pane shows. [nonce] makes every "new note" a fresh editor. */
private data class PaneNote(val noteId: Long, val checklist: Boolean, val labelId: Long?, val templateId: Long, val nonce: Long) {
    val key: String get() = "$noteId|$checklist|$labelId|$templateId|$nonce"

    companion object {
        fun parse(key: String): PaneNote? = key.split('|').takeIf { it.size == 5 }?.let {
            PaneNote(it[0].toLong(), it[1].toBoolean(), it[2].toLongOrNull(), it[3].toLong(), it[4].toLong())
        }
    }
}

/**
 * Holds the pane editor's view model. Switching notes clears the old one (which saves it,
 * as leaving the full-screen editor does); rotating the screen keeps it.
 */
class PaneEditorHolder : ViewModel() {
    private var key: String? = null
    private var store: ViewModelStore? = null

    fun storeFor(newKey: String): ViewModelStore {
        if (newKey != key) {
            store?.clear()
            store = ViewModelStore()
            key = newKey
        }
        return store!!
    }

    fun close() {
        store?.clear()
        store = null
        key = null
    }

    override fun onCleared() = close()
}

/** Callbacks the notes list uses to open things in the right-hand pane. */
class PaneActions(
    val openNote: (Long) -> Unit,
    val newNote: (checklist: Boolean, labelId: Long?) -> Unit,
    val newFromTemplate: (templateId: Long, labelId: Long?) -> Unit,
)

/**
 * The notes list on the left and the open note on the right. [list] draws the notes
 * list, wired to the given actions.
 */
@Composable
fun TwoPaneHome(list: @Composable (PaneActions) -> Unit) {
    var openKey by rememberSaveable { mutableStateOf<String?>(null) }
    val open = openKey?.let(PaneNote::parse)
    val holder: PaneEditorHolder = viewModel()
    val application = LocalContext.current.applicationContext as Application
    fun show(note: PaneNote) { openKey = note.key }
    fun nonce() = System.nanoTime()
    val actions = remember {
        PaneActions(
            openNote = { id -> show(PaneNote(id, false, null, 0, nonce())) },
            newNote = { checklist, labelId -> show(PaneNote(-1, checklist, labelId, 0, nonce())) },
            newFromTemplate = { templateId, labelId -> show(PaneNote(-1, false, labelId, templateId, nonce())) },
        )
    }

    Row(Modifier.fillMaxSize()) {
        Box(Modifier.weight(0.42f).fillMaxHeight()) { list(actions) }
        VerticalDivider()
        Box(Modifier.weight(0.58f).fillMaxHeight().testTag("detail-pane")) {
            if (open == null) {
                EmptyPane()
            } else key(open.key) {
                val store = holder.storeFor(open.key)
                val owner = remember(store) { object : ViewModelStoreOwner { override val viewModelStore = store } }
                val editor: EditorViewModel = viewModel(
                    viewModelStoreOwner = owner,
                    factory = EditorViewModel.paneFactory(open.noteId, open.checklist, open.labelId, open.templateId),
                    extras = MutableCreationExtras().apply { set(APPLICATION_KEY, application) },
                )
                EditorScreen(
                    viewModel = editor,
                    onBack = { holder.close(); openKey = null },
                    onOpenNote = { id -> show(PaneNote(id, false, null, 0, nonce())) },
                    // The list beside it already has a banner; one ad on screen is plenty.
                    bannerAd = {},
                )
            }
        }
    }
}

@Composable
private fun EmptyPane() {
    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
        Column(
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize().padding(32.dp),
        ) {
            Icon(
                Icons.AutoMirrored.Outlined.Notes,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(56.dp),
            )
            Text(
                "Select a note to read or edit it here",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}
