package com.holymanzion.simplenotepro.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.CheckBox
import androidx.glance.appwidget.CheckboxDefaults
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.holymanzion.simplenotepro.MainActivity
import com.holymanzion.simplenotepro.SimpleNoteApp
import com.holymanzion.simplenotepro.data.ChecklistItem
import com.holymanzion.simplenotepro.data.Note
import com.holymanzion.simplenotepro.reminder.ReminderScheduler
import com.holymanzion.simplenotepro.text.RichText
import com.holymanzion.simplenotepro.ui.theme.NoteColors

/** Which note each placed single-note widget shows, by app-widget id. */
object NoteWidgetPrefs {
    private fun prefs(context: Context) = context.getSharedPreferences("note_widgets", Context.MODE_PRIVATE)

    fun noteFor(context: Context, appWidgetId: Int): Long? =
        prefs(context).getLong("w_$appWidgetId", -1L).takeIf { it > 0 }

    fun set(context: Context, appWidgetId: Int, noteId: Long) =
        prefs(context).edit().putLong("w_$appWidgetId", noteId).apply()

    fun remove(context: Context, appWidgetIds: IntArray) =
        prefs(context).edit().apply { appWidgetIds.forEach { remove("w_$it") } }.apply()
}

private sealed interface NoteWidgetContent {
    data object Locked : NoteWidgetContent
    data object Missing : NoteWidgetContent
    data class Shown(val note: Note, val checkedToBottom: Boolean) : NoteWidgetContent
}

/**
 * One chosen note on the home screen. A checklist's items can be ticked right there; a
 * text note shows its text. Tapping the title opens the note in the app.
 */
class NoteWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val content = load(context, NoteWidgetPrefs.noteFor(context, appWidgetId))
        provideContent { GlanceTheme { Content(context, content) } }
    }

    private suspend fun load(context: Context, noteId: Long?): NoteWidgetContent {
        val container = (context.applicationContext as SimpleNoteApp).container
        val settings = container.settings.settings.value
        if (settings.appLock) return NoteWidgetContent.Locked
        val note = noteId?.let { container.repository.get(it)?.note }?.takeIf { !it.isTrashed }
            ?: return NoteWidgetContent.Missing
        return NoteWidgetContent.Shown(note, settings.checkedToBottom)
    }

    @Composable
    private fun Content(context: Context, content: NoteWidgetContent) {
        val note = (content as? NoteWidgetContent.Shown)?.note
        val colors = NoteColors.getOrNull(note?.color ?: 0)
        // Note colours have a light and a dark variant; "no colour" follows the widget theme.
        val background = if (colors == null || colors.light == Color.Unspecified) GlanceTheme.colors.widgetBackground
        else ColorProvider(day = colors.light, night = colors.dark)
        val text = if (colors == null || colors.light == Color.Unspecified) GlanceTheme.colors.onSurface
        else ColorProvider(day = Color(0xFF1F1B16), night = Color(0xFFEAE1D6))

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(background)
                .cornerRadius(20.dp)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            when (content) {
                NoteWidgetContent.Locked -> Message("Note is locked. Tap to open.", openApp(context, null), text)
                NoteWidgetContent.Missing -> Message(
                    "This note was deleted. Touch and hold the widget to choose another.",
                    openApp(context, null),
                    text,
                )
                is NoteWidgetContent.Shown -> ShownNote(context, content, text)
            }
        }
    }

    @Composable
    private fun ShownNote(context: Context, content: NoteWidgetContent.Shown, text: ColorProvider) {
        val note = content.note
        Text(
            text = note.title.ifBlank { if (note.isChecklist) "Checklist" else "Note" },
            style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp, color = text),
            maxLines = 1,
            modifier = GlanceModifier.fillMaxWidth().padding(start = 4.dp, bottom = 4.dp).clickable(actionStartActivity(openApp(context, note.id))),
        )
        if (note.isChecklist) {
            val items = if (content.checkedToBottom) note.items.sortedBy { it.checked } else note.items
            if (items.isEmpty()) {
                Message("No items yet. Tap the title to add some.", openApp(context, note.id), text)
            } else {
                LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                    items(items, itemId = { it.id }) { item -> ItemRow(note.id, item, text) }
                }
            }
        } else {
            val body = RichText.render(note.content, Color.Unspecified).text
            Text(
                text = body.ifBlank { "Empty note" },
                style = TextStyle(fontSize = 14.sp, color = text),
                maxLines = 14,
                modifier = GlanceModifier.fillMaxWidth().padding(horizontal = 4.dp).clickable(actionStartActivity(openApp(context, note.id))),
            )
        }
    }

    @Composable
    private fun ItemRow(noteId: Long, item: ChecklistItem, text: ColorProvider) {
        CheckBox(
            checked = item.checked,
            onCheckedChange = actionRunCallback<ToggleItemAction>(
                actionParametersOf(ToggleItemAction.NOTE_ID to noteId, ToggleItemAction.ITEM_ID to item.id),
            ),
            text = item.text,
            style = TextStyle(
                fontSize = 14.sp,
                color = text,
                textDecoration = if (item.checked) TextDecoration.LineThrough else TextDecoration.None,
            ),
            colors = CheckboxDefaults.colors(checkedColor = GlanceTheme.colors.primary, uncheckedColor = text),
            maxLines = 2,
            modifier = GlanceModifier.fillMaxWidth(),
        )
    }

    @Composable
    private fun Message(message: String, intent: Intent, text: ColorProvider) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = GlanceModifier.fillMaxSize().clickable(actionStartActivity(intent)),
        ) {
            Text(message, style = TextStyle(fontSize = 13.sp, color = text))
        }
    }

    private fun openApp(context: Context, noteId: Long?): Intent =
        Intent(context, MainActivity::class.java)
            .setAction(if (noteId != null) "com.holymanzion.simplenotepro.OPEN_NOTE.$noteId" else Intent.ACTION_MAIN)
            .apply { if (noteId != null) putExtra(ReminderScheduler.EXTRA_NOTE_ID, noteId) }
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
}

/** Ticks or unticks a checklist item from the widget, then redraws it straight away. */
class ToggleItemAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val noteId = parameters[NOTE_ID] ?: return
        val itemId = parameters[ITEM_ID] ?: return
        val container = (context.applicationContext as SimpleNoteApp).container
        if (container.settings.settings.value.appLock) return
        container.repository.toggleChecklistItem(noteId, itemId)
        NoteWidget().update(context, glanceId)
    }

    companion object {
        val NOTE_ID = ActionParameters.Key<Long>("noteId")
        val ITEM_ID = ActionParameters.Key<Long>("itemId")
    }
}

class NoteWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NoteWidget()

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        NoteWidgetPrefs.remove(context, appWidgetIds)
    }

    companion object {
        fun widgetIdFrom(intent: Intent?): Int =
            intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                ?: AppWidgetManager.INVALID_APPWIDGET_ID
    }
}
