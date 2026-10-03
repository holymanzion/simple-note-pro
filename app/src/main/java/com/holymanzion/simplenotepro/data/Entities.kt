package com.holymanzion.simplenotepro.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Junction
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String = "",
    val content: String = "",
    /** Checklist items encoded with [ChecklistCodec]; only meaningful when [isChecklist]. */
    val checklist: String = "",
    val isChecklist: Boolean = false,
    /** Index into [com.holymanzion.simplenotepro.ui.theme.NoteColors]; 0 is the default. */
    val color: Int = 0,
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val isTrashed: Boolean = false,
    val trashedAt: Long? = null,
    val reminderAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    /** [Repeat] name, or null for a one-off reminder. Added in schema v2. */
    val reminderRepeat: String? = null,
) {
    val repeat: Repeat? get() = Repeat.fromName(reminderRepeat)

    val items: List<ChecklistItem> get() = if (isChecklist) ChecklistCodec.decode(checklist) else emptyList()

    fun isBlank(): Boolean =
        title.isBlank() && content.isBlank() && items.all { it.text.isBlank() }

    /** Plain-text form used for sharing and search. */
    fun bodyText(): String =
        if (isChecklist) items.joinToString("\n") { (if (it.checked) "☑ " else "☐ ") + it.text } else content
}

@Entity(tableName = "labels", indices = [Index(value = ["name"], unique = true)])
data class Label(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
)

@Entity(
    tableName = "note_labels",
    primaryKeys = ["noteId", "labelId"],
    foreignKeys = [
        ForeignKey(entity = Note::class, parentColumns = ["id"], childColumns = ["noteId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Label::class, parentColumns = ["id"], childColumns = ["labelId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("labelId")],
)
data class NoteLabel(
    val noteId: Long,
    val labelId: Long,
)

/** An image attached to a note. The file lives in [AttachmentStore]'s directory. */
@Entity(
    tableName = "attachments",
    foreignKeys = [
        ForeignKey(entity = Note::class, parentColumns = ["id"], childColumns = ["noteId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("noteId")],
)
data class Attachment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val noteId: Long,
    val fileName: String,
    val width: Int,
    val height: Int,
    val createdAt: Long = System.currentTimeMillis(),
    /**
     * Text read from the picture, for search. Null = not read yet; "" = read, no text.
     * Added in schema v3.
     */
    val ocrText: String? = null,
)

data class NoteWithLabels(
    @Embedded val note: Note,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(NoteLabel::class, parentColumn = "noteId", entityColumn = "labelId"),
    )
    val labels: List<Label>,
    @Relation(parentColumn = "id", entityColumn = "noteId")
    val attachments: List<Attachment> = emptyList(),
)
