package com.holymanzion.simplenotepro.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Transaction
    @Query("SELECT * FROM notes")
    fun observeAll(): Flow<List<NoteWithLabels>>

    @Transaction
    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun get(id: Long): NoteWithLabels?

    @Transaction
    @Query("SELECT * FROM notes")
    suspend fun getAll(): List<NoteWithLabels>

    @Insert
    suspend fun insert(note: Note): Long

    @Update
    suspend fun update(note: Note)

    @Query("UPDATE notes SET isPinned = :pinned, updatedAt = :now WHERE id IN (:ids)")
    suspend fun setPinned(ids: List<Long>, pinned: Boolean, now: Long)

    @Query("UPDATE notes SET isArchived = :archived, isPinned = CASE WHEN :archived THEN 0 ELSE isPinned END, updatedAt = :now WHERE id IN (:ids)")
    suspend fun setArchived(ids: List<Long>, archived: Boolean, now: Long)

    @Query("UPDATE notes SET color = :color, updatedAt = :now WHERE id IN (:ids)")
    suspend fun setColor(ids: List<Long>, color: Int, now: Long)

    @Query("UPDATE notes SET isTrashed = 1, isPinned = 0, trashedAt = :now WHERE id IN (:ids)")
    suspend fun trash(ids: List<Long>, now: Long)

    @Query("UPDATE notes SET isTrashed = 0, trashedAt = NULL WHERE id IN (:ids)")
    suspend fun restore(ids: List<Long>)

    @Query("UPDATE notes SET reminderAt = :at, reminderRepeat = :repeat WHERE id = :id")
    suspend fun setReminder(id: Long, at: Long?, repeat: String?)

    @Query("DELETE FROM notes WHERE id IN (:ids)")
    suspend fun delete(ids: List<Long>)

    @Query("SELECT id FROM notes WHERE isTrashed = 1")
    suspend fun trashedIds(): List<Long>

    @Query("SELECT id FROM notes WHERE isTrashed = 1 AND trashedAt < :before")
    suspend fun trashedBefore(before: Long): List<Long>

    /** Future reminders, plus repeating ones whose last time passed (e.g. while the phone was off). */
    @Query(
        "SELECT * FROM notes WHERE reminderAt IS NOT NULL AND isTrashed = 0 " +
            "AND (reminderAt > :now OR reminderRepeat IS NOT NULL)",
    )
    suspend fun activeReminders(now: Long): List<Note>

    // --- Attachments ---

    @Query("SELECT * FROM attachments WHERE noteId = :noteId ORDER BY createdAt, id")
    fun observeAttachments(noteId: Long): Flow<List<Attachment>>

    @Query("SELECT * FROM attachments WHERE noteId IN (:noteIds)")
    suspend fun attachmentsFor(noteIds: List<Long>): List<Attachment>

    @Query("SELECT fileName FROM attachments")
    suspend fun allAttachmentFiles(): List<String>

    @Insert
    suspend fun insertAttachment(attachment: Attachment): Long

    @Query("DELETE FROM attachments WHERE id = :id")
    suspend fun deleteAttachment(id: Long)

    /** Pictures whose text hasn't been read yet. */
    @Query("SELECT * FROM attachments WHERE ocrText IS NULL ORDER BY id")
    suspend fun attachmentsWithoutText(): List<Attachment>

    @Query("UPDATE attachments SET ocrText = :text WHERE id = :id")
    suspend fun setAttachmentText(id: Long, text: String)

    // --- Labels ---

    @Query("SELECT * FROM labels ORDER BY name COLLATE NOCASE")
    fun observeLabels(): Flow<List<Label>>

    @Query("SELECT * FROM labels WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findLabel(name: String): Label?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLabel(label: Label): Long

    @Query("UPDATE labels SET name = :name WHERE id = :id")
    suspend fun renameLabel(id: Long, name: String)

    @Query("DELETE FROM labels WHERE id = :id")
    suspend fun deleteLabel(id: Long)

    @Query("DELETE FROM note_labels WHERE noteId = :noteId")
    suspend fun clearNoteLabels(noteId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertNoteLabels(refs: List<NoteLabel>)

    @Transaction
    suspend fun setNoteLabels(noteId: Long, labelIds: Collection<Long>) {
        clearNoteLabels(noteId)
        insertNoteLabels(labelIds.map { NoteLabel(noteId, it) })
    }
}
