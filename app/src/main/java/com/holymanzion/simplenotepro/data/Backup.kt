package com.holymanzion.simplenotepro.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Backups are a ZIP holding `notes.json` plus the note images under `attachments/`.
 * Importing merges into the current notes rather than replacing them, so restoring the
 * same file twice creates duplicates, never data loss. Plain `.json` backups from
 * version 1.0 still import.
 */
class BackupManager(private val repository: NoteRepository) {
    private val store get() = repository.attachmentStore

    /** Writes a ZIP backup to [out] and returns the number of notes in it. */
    suspend fun export(out: OutputStream): Int {
        val notes = repository.getAll()
        withContext(Dispatchers.IO) {
            ZipOutputStream(out.buffered()).use { zip ->
                zip.putNextEntry(ZipEntry(NOTES_ENTRY))
                zip.write(notesJson(notes).toString(2).toByteArray())
                zip.closeEntry()
                notes.flatMap { it.attachments }.forEach { attachment ->
                    val file = store.file(attachment.fileName)
                    if (!file.exists()) return@forEach
                    zip.putNextEntry(ZipEntry(ATTACHMENTS_DIR + attachment.fileName))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
        return notes.size
    }

    /** Returns the number of notes imported. Throws if the file is not a valid backup. */
    suspend fun import(input: InputStream): Int {
        val buffered = BufferedInputStream(input)
        buffered.mark(4)
        val isZip = buffered.read() == 'P'.code && buffered.read() == 'K'.code
        buffered.reset()

        val files = mutableMapOf<String, String>() // name in backup -> name in store
        val json = withContext(Dispatchers.IO) {
            if (!isZip) return@withContext buffered.bufferedReader().use { it.readText() }
            var notesText: String? = null
            ZipInputStream(buffered).use { zip ->
                generateSequence { zip.nextEntry }.forEach { entry ->
                    when {
                        entry.name == NOTES_ENTRY -> notesText = zip.readBytes().decodeToString()
                        // Only plain names under attachments/, so a crafted "../" entry can't escape.
                        entry.name.startsWith(ATTACHMENTS_DIR) && !entry.isDirectory -> {
                            val name = entry.name.removePrefix(ATTACHMENTS_DIR)
                            if (name.isNotEmpty() && '/' !in name && '\\' !in name && name != "..") {
                                files[name] = store.importBytes(zip, name.substringAfterLast('.', "jpg"))
                            }
                        }
                    }
                }
            }
            notesText ?: error("No notes.json in backup")
        }

        val notes = JSONObject(json).getJSONArray("notes")
        val labelIds = mutableMapOf<String, Long>()
        repository.inTransaction { importNotes(notes, labelIds, files) }
        return notes.length()
    }

    private fun notesJson(notes: List<NoteWithLabels>) = JSONObject()
        .put("app", "Simple Note Pro")
        .put("version", FORMAT_VERSION)
        .put("exportedAt", System.currentTimeMillis())
        .put("notes", JSONArray().apply {
            notes.forEach { (note, labels, attachments) ->
                put(
                    JSONObject()
                        .put("title", note.title)
                        .put("content", note.content)
                        .put("checklist", note.checklist)
                        .put("isChecklist", note.isChecklist)
                        .put("color", note.color)
                        .put("isPinned", note.isPinned)
                        .put("isArchived", note.isArchived)
                        .put("isTrashed", note.isTrashed)
                        .put("trashedAt", note.trashedAt ?: JSONObject.NULL)
                        .put("reminderAt", note.reminderAt ?: JSONObject.NULL)
                        .put("reminderRepeat", note.reminderRepeat ?: JSONObject.NULL)
                        .put("createdAt", note.createdAt)
                        .put("updatedAt", note.updatedAt)
                        .put("labels", JSONArray(labels.map { it.name }))
                        .put("attachments", JSONArray().apply {
                            attachments.forEach {
                                put(JSONObject().put("file", it.fileName).put("width", it.width).put("height", it.height).put("text", it.ocrText ?: JSONObject.NULL))
                            }
                        })
                )
            }
        })

    private suspend fun importNotes(notes: JSONArray, labelIds: MutableMap<String, Long>, files: Map<String, String>) {
        for (i in 0 until notes.length()) {
            val o = notes.getJSONObject(i)
            val names = o.optJSONArray("labels") ?: JSONArray()
            val ids = (0 until names.length()).mapNotNull { j ->
                val name = names.getString(j)
                labelIds[name.lowercase()] ?: repository.createLabel(name)?.also { labelIds[name.lowercase()] = it }
            }
            val note = Note(
                title = o.optString("title"),
                content = o.optString("content"),
                checklist = o.optString("checklist"),
                isChecklist = o.optBoolean("isChecklist"),
                color = o.optInt("color"),
                isPinned = o.optBoolean("isPinned"),
                isArchived = o.optBoolean("isArchived"),
                isTrashed = o.optBoolean("isTrashed"),
                trashedAt = o.optLongOrNull("trashedAt"),
                reminderAt = o.optLongOrNull("reminderAt"),
                reminderRepeat = Repeat.fromName(o.optStringOrNull("reminderRepeat"))?.name,
                createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                updatedAt = o.optLong("updatedAt", System.currentTimeMillis()),
            )
            val noteId = repository.save(note, ids)
            val attachments = o.optJSONArray("attachments") ?: JSONArray()
            for (j in 0 until attachments.length()) {
                val a = attachments.getJSONObject(j)
                val stored = files[a.optString("file")] ?: continue
                repository.addStoredImage(noteId, stored, a.optInt("width"), a.optInt("height"), a.optStringOrNull("text"))
            }
        }
    }

    private fun JSONObject.optLongOrNull(key: String): Long? =
        if (isNull(key)) null else optLong(key)

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (isNull(key)) null else optString(key)

    private companion object {
        const val FORMAT_VERSION = 2
        const val NOTES_ENTRY = "notes.json"
        const val ATTACHMENTS_DIR = "attachments/"
    }
}
