package com.holymanzion.simplenotepro

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.holymanzion.simplenotepro.data.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Version 1.0.x phones have a v1 database; upgrading must keep every note and label. */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val dbName = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @Test
    fun v1ToV2_keepsNotesLabelsAndLinks() {
        helper.createDatabase(dbName, 1).use { db ->
            db.execSQL(
                "INSERT INTO notes (id, title, content, checklist, isChecklist, color, isPinned, isArchived, isTrashed, " +
                    "trashedAt, reminderAt, createdAt, updatedAt) " +
                    "VALUES (7, 'Old note', 'Written in 1.0', '', 0, 3, 1, 0, 0, NULL, 1900000000000, 1000, 2000)",
            )
            db.execSQL("INSERT INTO labels (id, name) VALUES (1, 'Work')")
            db.execSQL("INSERT INTO note_labels (noteId, labelId) VALUES (7, 1)")
        }

        // Room validates each migrated schema and fails if it differs from the real one.
        helper.runMigrationsAndValidate(dbName, 2, true).close()
        helper.runMigrationsAndValidate(dbName, 3, true).close()

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbName).build()
        try {
            val note = runBlocking { db.noteDao().get(7) }!!
            assertEquals("Old note", note.note.title)
            assertEquals("Written in 1.0", note.note.content)
            assertEquals(3, note.note.color)
            assertEquals(1900000000000, note.note.reminderAt)
            assertNull(note.note.reminderRepeat)
            assertEquals(listOf("Work"), note.labels.map { it.name })
            assertEquals(emptyList<Any>(), note.attachments)
        } finally {
            db.close()
            context.deleteDatabase(dbName)
        }
    }

    /** 1.1.x phones have v2 with pictures; v3 must keep them and mark their text unread. */
    @Test
    fun v2ToV3_keepsPicturesWithTextUnread() {
        helper.createDatabase(dbName, 2).use { db ->
            db.execSQL(
                "INSERT INTO notes (id, title, content, checklist, isChecklist, color, isPinned, isArchived, isTrashed, " +
                    "trashedAt, reminderAt, createdAt, updatedAt, reminderRepeat) " +
                    "VALUES (3, 'Receipt', '', '', 0, 0, 0, 0, 0, NULL, NULL, 1000, 2000, 'WEEKLY')",
            )
            db.execSQL("INSERT INTO attachments (id, noteId, fileName, width, height, createdAt) VALUES (9, 3, 'a.jpg', 800, 600, 1500)")
        }
        helper.runMigrationsAndValidate(dbName, 3, true).close()

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbName).build()
        try {
            val note = runBlocking { db.noteDao().get(3) }!!
            assertEquals("WEEKLY", note.note.reminderRepeat)
            val picture = note.attachments.single()
            assertEquals("a.jpg", picture.fileName)
            assertNull(picture.ocrText) // so it gets read after the upgrade
        } finally {
            db.close()
            context.deleteDatabase(dbName)
        }
    }
}
