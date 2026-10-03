package com.holymanzion.simplenotepro.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * v1: notes, labels, note_labels.
 * v2: notes.reminderRepeat, attachments table (additive, so Room migrates automatically).
 * v3: attachments.ocrText (additive).
 */
@Database(
    entities = [Note::class, Label::class, NoteLabel::class, Attachment::class],
    version = 3,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3)],
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "simple_note_pro.db").build()
    }
}
