package com.holymanzion.simplenotepro.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * A starting point for new notes. The user's own templates are stored in the database
 * (schema v4); built-in ones live in [BuiltInTemplates] with negative ids so the two can
 * share one id space.
 */
@Entity(tableName = "templates")
data class NoteTemplate(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val title: String = "",
    val content: String = "",
    /** Checklist items encoded with [ChecklistCodec]; only meaningful when [isChecklist]. */
    val checklist: String = "",
    val isChecklist: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val isBuiltIn: Boolean get() = id < 0

    val items: List<ChecklistItem> get() = if (isChecklist) ChecklistCodec.decode(checklist) else emptyList()

    /** One-line preview for the template picker. */
    fun summary(): String {
        val text = if (isChecklist) items.joinToString(", ") { it.text }.ifBlank { "Checklist" }
        else content.lines().map { it.trim().trimStart('#', '-', '*', ' ').replace("**", "") }.filter { it.isNotBlank() }.joinToString(" · ")
        return text.take(80)
    }

    /** The note's starting text, with `{date}` replaced by [today]. */
    fun filled(today: LocalDate = LocalDate.now()): NoteTemplate {
        val date = today.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
        return copy(title = title.replace(DATE, date), content = content.replace(DATE, date))
    }

    companion object {
        /** Placeholder that becomes the current date when a note is made from the template. */
        const val DATE = "{date}"
    }
}

object BuiltInTemplates {
    private fun list(id: Long, name: String, title: String, vararg items: String) = NoteTemplate(
        id = id,
        name = name,
        title = title,
        isChecklist = true,
        checklist = ChecklistCodec.encode(items.mapIndexed { i, text -> ChecklistItem(i + 1L, text) }),
        createdAt = 0,
    )

    private fun text(id: Long, name: String, title: String, content: String) =
        NoteTemplate(id = id, name = name, title = title, content = content, createdAt = 0)

    val all: List<NoteTemplate> = listOf(
        list(-1, "Shopping list", "Shopping", "Milk", "Bread", "Eggs", "Fruit", "Vegetables"),
        text(
            -2, "Meeting notes", "Meeting · ${NoteTemplate.DATE}",
            "**Attendees:** \n\n## Agenda\n- \n\n## Notes\n\n\n## Action items\n- ",
        ),
        text(
            -3, "Sermon notes", "Sermon · ${NoteTemplate.DATE}",
            "**Preacher:** \n**Scripture:** \n\n## Main points\n1. \n\n## Key verses\n- \n\n## Application\n- ",
        ),
        text(
            -4, "Journal", NoteTemplate.DATE,
            "**I'm grateful for:**\n- \n\n**What happened today:**\n\n\n**Tomorrow I want to:**\n- ",
        ),
        list(-5, "Packing list", "Packing", "Clothes", "Toiletries", "Phone charger", "Documents", "Medication"),
        text(-6, "Recipe", "", "## Ingredients\n- \n\n## Steps\n1. "),
        list(-7, "To-do list", "To-do · ${NoteTemplate.DATE}", ""),
    )

    fun get(id: Long): NoteTemplate? = all.firstOrNull { it.id == id }
}
