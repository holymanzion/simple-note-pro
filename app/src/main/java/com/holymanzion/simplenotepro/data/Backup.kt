package com.holymanzion.simplenotepro.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import net.lingala.zip4j.exception.ZipException
import net.lingala.zip4j.io.inputstream.ZipInputStream
import net.lingala.zip4j.io.outputstream.ZipOutputStream
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.CompressionMethod
import net.lingala.zip4j.model.enums.EncryptionMethod
import java.io.BufferedInputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * Backups are a ZIP holding `notes.json` plus the note images under `attachments/`.
 * With a backup password set, every entry is AES-256 encrypted (standard WinZip AES, so
 * 7-Zip and similar tools can open it with the password too).
 * Importing merges into the current notes rather than replacing them, so restoring the
 * same file twice creates duplicates, never data loss. Plain `.json` backups from
 * version 1.0 still import.
 */
class BackupManager(
    private val repository: NoteRepository,
    private val passwords: BackupPasswordStore? = null,
) {
    private val store get() = repository.attachmentStore

    /**
     * Writes a ZIP backup to [out] and returns the number of notes in it. Encrypted when
     * [password] is given, which defaults to the backup password set in Settings.
     */
    suspend fun export(out: OutputStream, password: CharArray? = passwords?.get()): Int {
        val notes = repository.getAll()
        withContext(Dispatchers.IO) {
            val zip = if (password != null) ZipOutputStream(out.buffered(), password) else ZipOutputStream(out.buffered())
            zip.use {
                zip.putNextEntry(entry(NOTES_ENTRY, password, CompressionMethod.DEFLATE))
                zip.write(notesJson(notes).toString(2).toByteArray())
                zip.closeEntry()
                notes.flatMap { it.attachments }.forEach { attachment ->
                    val file = store.file(attachment.fileName)
                    if (!file.exists()) return@forEach
                    // JPEGs are already compressed; storing them as-is is just as small and faster.
                    zip.putNextEntry(entry(ATTACHMENTS_DIR + attachment.fileName, password, CompressionMethod.STORE, size = file.length()))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
        return notes.size
    }

    /** [size] is required for STORE entries: an uncompressed entry's size goes in its header. */
    private fun entry(name: String, password: CharArray?, method: CompressionMethod, size: Long? = null) = ZipParameters().apply {
        fileNameInZip = name
        compressionMethod = method
        if (size != null) entrySize = size
        if (password != null) {
            isEncryptFiles = true
            encryptionMethod = EncryptionMethod.AES
            aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256
        }
    }

    /**
     * Returns the number of notes imported. A password-protected backup is opened with
     * [password], or else the backup password saved on this phone; if neither works this
     * throws [BackupPasswordException] before anything is imported, so the caller can ask
     * for the password and try again. Throws other exceptions for files that aren't backups.
     */
    suspend fun import(input: InputStream, password: CharArray? = null): Int {
        val buffered = BufferedInputStream(input)
        buffered.mark(4)
        val isZip = buffered.read() == 'P'.code && buffered.read() == 'K'.code
        buffered.reset()

        val effectivePassword = password ?: passwords?.get()
        val files = mutableMapOf<String, String>() // name in backup -> name in store
        val json = withContext(Dispatchers.IO) {
            if (!isZip) return@withContext buffered.bufferedReader().use { it.readText() }
            var notesText: String? = null
            try {
                ZipInputStream(buffered, effectivePassword).use { zip ->
                    generateSequence { zip.nextEntry }.forEach { entry ->
                        if (entry.isEncrypted && effectivePassword == null) throw BackupPasswordException(wrongPassword = false)
                        val name = entry.fileName
                        when {
                            name == NOTES_ENTRY -> notesText = zip.readBytes().decodeToString()
                            // Only plain names under attachments/, so a crafted "../" entry can't escape.
                            name.startsWith(ATTACHMENTS_DIR) && !entry.isDirectory -> {
                                val file = name.removePrefix(ATTACHMENTS_DIR)
                                if (file.isNotEmpty() && '/' !in file && '\\' !in file && file != "..") {
                                    files[file] = store.importBytes(zip, file.substringAfterLast('.', "jpg"))
                                }
                            }
                        }
                    }
                }
            } catch (e: ZipException) {
                if (e.type != ZipException.Type.WRONG_PASSWORD) throw e
                // A password the user typed is simply wrong; a saved one that doesn't fit
                // (the backup came from another phone) means we need to ask.
                throw BackupPasswordException(wrongPassword = password != null)
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
