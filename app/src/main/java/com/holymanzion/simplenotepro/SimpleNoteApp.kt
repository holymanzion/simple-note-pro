package com.holymanzion.simplenotepro

import android.app.Application
import android.content.Context
import com.holymanzion.simplenotepro.backup.AutoBackupWorker
import com.holymanzion.simplenotepro.data.AppDatabase
import com.holymanzion.simplenotepro.data.AttachmentStore
import com.holymanzion.simplenotepro.data.BackupManager
import com.holymanzion.simplenotepro.data.NoteRepository
import com.holymanzion.simplenotepro.data.SettingsRepository
import com.holymanzion.simplenotepro.lock.AppLock
import com.holymanzion.simplenotepro.reminder.ReminderScheduler
import com.holymanzion.simplenotepro.widget.NotesWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AppContainer(context: Context) {
    /** Outlives any screen, so saves started while leaving the editor still finish. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val repository = NoteRepository(AppDatabase.create(context), ReminderScheduler(context), AttachmentStore(context))
    val settings = SettingsRepository(context)
    val backup = BackupManager(repository)
    val appLock = AppLock(isEnabled = { settings.settings.value.appLock })
}

class SimpleNoteApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        ReminderScheduler.createChannel(this)
        AutoBackupWorker.schedule(this, container.settings.settings.value.backupFrequency)
        container.appScope.launch {
            container.repository.purgeExpiredTrash()
            container.repository.deleteOrphanImages()
            // Pictures added before text search existed, or whose reading was cut short.
            container.repository.readPendingImageText()
        }
        NotesWidget.keepUpdated(this, container)
    }
}
