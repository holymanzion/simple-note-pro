package com.holymanzion.simplenotepro.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.lifecycleScope
import com.holymanzion.simplenotepro.SimpleNoteApp
import com.holymanzion.simplenotepro.data.NoteWithLabels
import com.holymanzion.simplenotepro.lock.DeviceAuth
import com.holymanzion.simplenotepro.lock.LockScreen
import com.holymanzion.simplenotepro.text.RichText
import com.holymanzion.simplenotepro.ui.theme.SimpleNoteTheme
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Shown by the launcher when a single-note widget is placed (and, on Android 12+, when
 * you touch and hold it to reconfigure): pick which note it shows.
 */
class NoteWidgetConfigActivity : FragmentActivity() {
    private val container by lazy { (application as SimpleNoteApp).container }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Backing out without choosing must not leave an empty widget behind.
        setResult(Activity.RESULT_CANCELED)
        val appWidgetId = NoteWidgetReceiver.widgetIdFrom(intent)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        enableEdgeToEdge()
        setContent {
            val settings by container.settings.settings.collectAsState()
            // With the app lock on, note titles are private too: unlock first.
            var unlocked by remember { mutableStateOf(!settings.appLock) }
            SimpleNoteTheme(themeMode = settings.themeMode, dynamicColor = settings.dynamicColor) {
                if (!unlocked) {
                    LockScreen(promptAutomatically = true) { onError ->
                        DeviceAuth.authenticate(this, "Unlock to choose a note", onSuccess = { unlocked = true }, onError = onError)
                    }
                } else {
                    NotePicker(onPick = { choose(appWidgetId, it) }, onClose = ::finish)
                }
            }
        }
    }

    private fun choose(appWidgetId: Int, noteId: Long) {
        NoteWidgetPrefs.set(this, appWidgetId, noteId)
        lifecycleScope.launch {
            // The choice is saved either way; the widget also redraws on the next note change.
            runCatching {
                val glanceId = GlanceAppWidgetManager(this@NoteWidgetConfigActivity).getGlanceIdBy(appWidgetId)
                NoteWidget().update(this@NoteWidgetConfigActivity, glanceId)
            }
            setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
            finish()
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun NotePicker(onPick: (Long) -> Unit, onClose: () -> Unit) {
        val notes by remember {
            container.repository.notes.map { all ->
                all.filter { !it.note.isTrashed }
                    .sortedWith(compareByDescending<NoteWithLabels> { it.note.isPinned }.thenByDescending { it.note.updatedAt })
            }
        }.collectAsState(initial = null)
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Choose a note for the widget") },
                    navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, contentDescription = "Cancel") } },
                )
            },
        ) { padding ->
            val list = notes
            when {
                list == null -> Unit
                list.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding).padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("No notes yet. Write one in Simple Note Pro first.", style = MaterialTheme.typography.bodyLarge)
                }
                else -> LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                    items(list, key = { it.note.id }) { (note) ->
                        val body = if (note.isChecklist) note.items.joinToString(" · ") { it.text }
                        else RichText.render(note.content, Color.Unspecified).text.replace('\n', ' ')
                        ListItem(
                            headlineContent = {
                                Text(note.title.ifBlank { body.ifBlank { "Untitled" } }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            supportingContent = if (note.title.isNotBlank() && body.isNotBlank()) {
                                { Text(body, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            } else null,
                            leadingContent = {
                                Icon(if (note.isChecklist) Icons.Outlined.CheckBox else Icons.AutoMirrored.Outlined.Notes, contentDescription = null)
                            },
                            modifier = Modifier.clickable { onPick(note.id) },
                        )
                    }
                }
            }
        }
    }
}
