package com.holymanzion.simplenotepro.data

import android.net.Uri
import androidx.room.withTransaction
import com.holymanzion.simplenotepro.reminder.ReminderScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class NoteRepository(
    private val database: AppDatabase,
    private val reminders: ReminderScheduler,
    val attachmentStore: AttachmentStore,
    private val textReader: ImageTextReader = ImageTextReader(),
) {
    private val dao: NoteDao = database.noteDao()

    /** Runs [block] as one transaction: bulk writes commit once and observers reload once. */
    suspend fun <R> inTransaction(block: suspend () -> R): R = database.withTransaction(block)

    val notes: Flow<List<NoteWithLabels>> = dao.observeAll()
    val labels: Flow<List<Label>> = dao.observeLabels()

    suspend fun get(id: Long): NoteWithLabels? = dao.get(id)

    suspend fun getAll(): List<NoteWithLabels> = dao.getAll()

    /** Inserts or updates [note] with the given labels and returns its id. */
    suspend fun save(note: Note, labelIds: Collection<Long>): Long {
        val id = if (note.id == 0L) dao.insert(note) else note.id.also { dao.update(note) }
        dao.setNoteLabels(id, labelIds)
        syncReminder(note.copy(id = id))
        return id
    }

    suspend fun setPinned(ids: List<Long>, pinned: Boolean) = dao.setPinned(ids, pinned, now())

    suspend fun setArchived(ids: List<Long>, archived: Boolean) = dao.setArchived(ids, archived, now())

    suspend fun setColor(ids: List<Long>, color: Int) = dao.setColor(ids, color, now())

    suspend fun trash(ids: List<Long>) {
        dao.trash(ids, now())
        ids.forEach(reminders::cancel)
    }

    suspend fun restore(ids: List<Long>) {
        dao.restore(ids)
        ids.forEach { id -> dao.get(id)?.note?.let { resumeReminder(it) } }
    }

    /** Schedules [note]'s reminder, first rolling a lapsed repeating one forward. */
    private suspend fun resumeReminder(note: Note) {
        val at = note.reminderAt
        val repeat = note.repeat
        if (at != null && repeat != null && at <= now()) setReminder(note.id, repeat.nextAfter(at, now()), repeat)
        else syncReminder(note)
    }

    suspend fun deleteForever(ids: List<Long>) {
        if (ids.isEmpty()) return
        ids.forEach(reminders::cancel)
        // Rows cascade with the note; the image files have to be removed by hand.
        val files = dao.attachmentsFor(ids).map { it.fileName }
        dao.delete(ids)
        withContext(Dispatchers.IO) { attachmentStore.delete(files) }
    }

    suspend fun emptyTrash() = deleteForever(dao.trashedIds())

    /** Notes stay in the trash for [TRASH_RETENTION_DAYS] before being removed for good. */
    suspend fun purgeExpiredTrash() {
        val cutoff = now() - TimeUnit.DAYS.toMillis(TRASH_RETENTION_DAYS)
        val expired = dao.trashedBefore(cutoff)
        if (expired.isNotEmpty()) deleteForever(expired)
    }

    /** Deletes image files left behind if the app died between writing a file and its row. */
    suspend fun deleteOrphanImages() = withContext(Dispatchers.IO) {
        attachmentStore.deleteOrphans(dao.allAttachmentFiles().toSet())
    }

    suspend fun setReminder(id: Long, at: Long?, repeat: Repeat? = null) {
        dao.setReminder(id, at, if (at == null) null else repeat?.name)
        dao.get(id)?.note?.let { syncReminder(it) }
    }

    /**
     * Called when a reminder goes off. A repeating reminder moves on to its next time and is
     * scheduled again; a one-off stays as it is (shown as past). Returns the note to announce.
     */
    suspend fun onReminderFired(id: Long): Note? {
        val note = dao.get(id)?.note ?: return null
        if (note.isTrashed) return null
        val repeat = note.repeat
        val at = note.reminderAt
        if (repeat != null && at != null) setReminder(id, repeat.nextAfter(at, now()), repeat)
        return note
    }

    suspend fun duplicate(id: Long): Long? {
        val source = dao.get(id) ?: return null
        val time = now()
        val copy = source.note.copy(
            id = 0,
            isPinned = false,
            reminderAt = null,
            reminderRepeat = null,
            createdAt = time,
            updatedAt = time,
        )
        val copyId = save(copy, source.labels.map { it.id })
        source.attachments.forEach { attachment ->
            val file = withContext(Dispatchers.IO) { attachmentStore.copy(attachment.fileName) }
            dao.insertAttachment(attachment.copy(id = 0, noteId = copyId, fileName = file))
        }
        return copyId
    }

    /** After a reboot or update: re-register alarms, rolling missed repeats forward. */
    suspend fun rescheduleAllReminders() {
        dao.activeReminders(now()).forEach { resumeReminder(it) }
    }

    private fun syncReminder(note: Note) {
        val at = note.reminderAt
        if (at != null && at > now() && !note.isTrashed) reminders.schedule(note) else reminders.cancel(note.id)
    }

    // --- Images ---

    fun observeAttachments(noteId: Long): Flow<List<Attachment>> = dao.observeAttachments(noteId)

    /** Copies the picked image into app storage and attaches it to [noteId]. */
    suspend fun addImage(noteId: Long, uri: Uri): Attachment {
        val stored = withContext(Dispatchers.IO) { attachmentStore.import(uri) }
        val attachment = Attachment(noteId = noteId, fileName = stored.fileName, width = stored.width, height = stored.height)
        return attachment.copy(id = dao.insertAttachment(attachment))
    }

    suspend fun addStoredImage(noteId: Long, fileName: String, width: Int, height: Int, ocrText: String? = null) {
        dao.insertAttachment(Attachment(noteId = noteId, fileName = fileName, width = width, height = height, ocrText = ocrText))
    }

    private val textReadingLock = Mutex()

    /**
     * Reads the text from every picture that hasn't been read yet (new ones, imports,
     * pictures from before this feature). Safe to call often: overlapping calls wait
     * rather than reading the same picture twice. Returns how many were read.
     */
    suspend fun readPendingImageText(): Int = textReadingLock.withLock {
        var read = 0
        for (attachment in dao.attachmentsWithoutText()) {
            val file = attachmentStore.file(attachment.fileName)
            if (!file.exists()) continue
            // A failure (e.g. a corrupt file) is recorded as "no text" so it isn't retried forever.
            val text = runCatching { textReader.read(file) }.getOrDefault("")
            dao.setAttachmentText(attachment.id, text)
            read++
        }
        read
    }

    suspend fun removeAttachment(attachment: Attachment) {
        dao.deleteAttachment(attachment.id)
        withContext(Dispatchers.IO) { attachmentStore.delete(listOf(attachment.fileName)) }
    }

    // --- Labels ---

    /** Creates a label, or returns the existing one with the same name. */
    suspend fun createLabel(name: String): Long? {
        val clean = name.trim()
        if (clean.isEmpty()) return null
        dao.findLabel(clean)?.let { return it.id }
        return dao.insertLabel(Label(name = clean)).takeIf { it > 0 }
    }

    /** Returns false when another label already uses [name]. */
    suspend fun renameLabel(id: Long, name: String): Boolean {
        val clean = name.trim()
        if (clean.isEmpty()) return false
        val existing = dao.findLabel(clean)
        if (existing != null && existing.id != id) return false
        dao.renameLabel(id, clean)
        return true
    }

    suspend fun deleteLabel(id: Long) = dao.deleteLabel(id)

    private fun now() = System.currentTimeMillis()

    companion object {
        const val TRASH_RETENTION_DAYS = 30L
    }
}
