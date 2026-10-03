package com.holymanzion.simplenotepro.backup

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.holymanzion.simplenotepro.SimpleNoteApp
import com.holymanzion.simplenotepro.data.BackupFrequency
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/** Writes a ZIP backup into the folder chosen in Settings and keeps the newest [KEEP]. */
class AutoBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as SimpleNoteApp).container
        val settings = container.settings
        val folder = settings.settings.value.backupFolder ?: return Result.success()
        return try {
            val dir = DocumentFile.fromTreeUri(applicationContext, Uri.parse(folder))
            if (dir == null || !dir.canWrite()) error("Can't write to the backup folder. Choose it again in Settings.")
            val name = PREFIX + LocalDateTime.now().format(STAMP) + ".zip"
            val file = dir.createFile("application/zip", name) ?: error("Couldn't create $name")
            applicationContext.contentResolver.openOutputStream(file.uri)?.use { container.backup.export(it) }
                ?: error("Couldn't open $name")
            prune(dir)
            settings.update { it.copy(lastBackupAt = System.currentTimeMillis(), lastBackupError = null) }
            Result.success()
        } catch (e: Exception) {
            settings.update { it.copy(lastBackupError = e.message ?: e.javaClass.simpleName) }
            // A missing folder won't fix itself; anything else (disk busy) is worth a retry.
            if (runAttemptCount < 3 && e !is IllegalStateException) Result.retry() else Result.failure()
        }
    }

    private fun prune(dir: DocumentFile) {
        dir.listFiles()
            .filter { it.name?.startsWith(PREFIX) == true }
            .sortedByDescending { it.name } // timestamped names sort by age
            .drop(KEEP)
            .forEach { it.delete() }
    }

    companion object {
        private const val PERIODIC = "auto-backup"
        private const val NOW = "backup-now"
        private const val PREFIX = "SimpleNotePro-auto-"
        private const val KEEP = 7
        private val STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")

        /** (Re)schedules the periodic backup to match [frequency]; OFF cancels it. */
        fun schedule(context: Context, frequency: BackupFrequency) {
            val work = WorkManager.getInstance(context)
            if (frequency == BackupFrequency.OFF) {
                work.cancelUniqueWork(PERIODIC)
                return
            }
            val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(frequency.days, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                .build()
            work.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        fun runNow(context: Context) {
            WorkManager.getInstance(context)
                .enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<AutoBackupWorker>().build())
        }
    }
}
