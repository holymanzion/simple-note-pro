package com.holymanzion.simplenotepro.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.holymanzion.simplenotepro.AppContainer
import com.holymanzion.simplenotepro.MainActivity
import com.holymanzion.simplenotepro.R
import com.holymanzion.simplenotepro.SimpleNoteApp
import com.holymanzion.simplenotepro.reminder.ReminderScheduler
import com.holymanzion.simplenotepro.text.RichText
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** What one note looks like on the widget. */
private data class WidgetNote(val id: Long, val title: String, val snippet: String, val isChecklist: Boolean)

private data class WidgetContent(val locked: Boolean, val heading: String, val notes: List<WidgetNote>)

/**
 * Home-screen widget: pinned notes (or, with none pinned, the most recent), plus
 * buttons for a new note and a new checklist. With the app lock on it shows no note
 * contents, only a button to open the app.
 */
class NotesWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val content = load(context)
        provideContent {
            GlanceTheme { Content(context, content) }
        }
    }

    private suspend fun load(context: Context): WidgetContent {
        val container = (context.applicationContext as SimpleNoteApp).container
        if (container.settings.settings.value.appLock) return WidgetContent(locked = true, heading = "", notes = emptyList())
        val active = container.repository.getAll()
            .filter { !it.note.isTrashed && !it.note.isArchived }
            .sortedByDescending { it.note.updatedAt }
        val pinned = active.filter { it.note.isPinned }
        val shown = (pinned.ifEmpty { active }).take(MAX_NOTES)
        return WidgetContent(
            locked = false,
            heading = if (pinned.isNotEmpty()) "Pinned" else "Recent notes",
            notes = shown.map { (note, _, attachments) ->
                val body = if (note.isChecklist) {
                    note.items.filter { !it.checked }.joinToString(" · ") { it.text }
                } else {
                    RichText.render(note.content, Color.Unspecified).text
                }
                WidgetNote(
                    id = note.id,
                    title = note.title.ifBlank { body.lineSequence().firstOrNull()?.take(60).orEmpty() }
                        .ifBlank { if (attachments.isNotEmpty()) "Picture" else "Untitled" },
                    snippet = body.replace('\n', ' ').take(160),
                    isChecklist = note.isChecklist,
                )
            },
        )
    }

    @Composable
    private fun Content(context: Context, content: WidgetContent) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .cornerRadius(20.dp)
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.fillMaxWidth().padding(start = 6.dp)) {
                Text(
                    text = if (content.locked) "Simple Note Pro" else content.heading,
                    style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 15.sp, color = GlanceTheme.colors.onSurface),
                    maxLines = 1,
                    modifier = GlanceModifier.defaultWeight(),
                )
                HeaderButton(R.drawable.ic_widget_checklist, "New checklist", openApp(context, MainActivity.ACTION_NEW_CHECKLIST))
                HeaderButton(R.drawable.ic_widget_add, "New note", openApp(context, MainActivity.ACTION_NEW_NOTE))
            }
            Spacer(GlanceModifier.height(6.dp))
            when {
                content.locked -> Message("Notes are locked. Tap to open.", openApp(context, Intent.ACTION_MAIN))
                content.notes.isEmpty() -> Message("No notes yet. Tap + to write one.", openApp(context, MainActivity.ACTION_NEW_NOTE))
                else -> LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                    items(content.notes, itemId = { it.id }) { note ->
                        Column(modifier = GlanceModifier.fillMaxWidth().padding(bottom = 6.dp)) {
                            NoteRow(context, note)
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun HeaderButton(icon: Int, label: String, intent: Intent) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = GlanceModifier.size(40.dp).cornerRadius(20.dp).clickable(actionStartActivity(intent)),
        ) {
            Image(
                provider = ImageProvider(icon),
                contentDescription = label,
                colorFilter = ColorFilter.tint(GlanceTheme.colors.primary),
                modifier = GlanceModifier.size(24.dp),
            )
        }
    }

    @Composable
    private fun NoteRow(context: Context, note: WidgetNote) {
        val open = Intent(context, MainActivity::class.java)
            .setAction(ACTION_OPEN_NOTE_PREFIX + note.id) // distinct action per note keeps each tap's intent separate
            .putExtra(ReminderScheduler.EXTRA_NOTE_ID, note.id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        Column(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(GlanceTheme.colors.surface)
                .cornerRadius(14.dp)
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .clickable(actionStartActivity(open)),
        ) {
            Text(
                text = note.title,
                style = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, color = GlanceTheme.colors.onSurface),
                maxLines = 1,
            )
            if (note.snippet.isNotBlank() && note.snippet != note.title) {
                Text(
                    text = note.snippet,
                    style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.onSurfaceVariant),
                    maxLines = 2,
                )
            }
        }
    }

    @Composable
    private fun Message(text: String, intent: Intent) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = GlanceModifier.fillMaxSize().clickable(actionStartActivity(intent)),
        ) {
            Text(text, style = TextStyle(fontSize = 13.sp, color = GlanceTheme.colors.onSurfaceVariant))
        }
    }

    private fun openApp(context: Context, action: String) =
        Intent(context, MainActivity::class.java)
            .setAction(action)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    companion object {
        private const val MAX_NOTES = 12
        private const val ACTION_OPEN_NOTE_PREFIX = "com.holymanzion.simplenotepro.OPEN_NOTE."

        /**
         * Refreshes placed widgets whenever notes or the lock setting change, for as long as
         * the app process is alive. Debounced so typing (autosave) doesn't redraw constantly.
         */
        @OptIn(FlowPreview::class)
        fun keepUpdated(context: Context, container: AppContainer) {
            val appContext = context.applicationContext
            container.appScope.launch {
                combine(container.repository.notes, container.settings.settings) { notes, settings -> notes to settings.appLock }
                    .distinctUntilChanged()
                    .debounce(1_000)
                    .collect { runCatching { NotesWidget().updateAll(appContext) } }
            }
        }
    }
}

class NotesWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NotesWidget()
}
