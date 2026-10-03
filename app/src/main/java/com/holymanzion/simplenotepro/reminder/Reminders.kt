package com.holymanzion.simplenotepro.reminder

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.holymanzion.simplenotepro.MainActivity
import com.holymanzion.simplenotepro.R
import com.holymanzion.simplenotepro.SimpleNoteApp
import com.holymanzion.simplenotepro.data.Note
import kotlinx.coroutines.launch

class ReminderScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun schedule(note: Note) {
        val at = note.reminderAt ?: return
        val pending = pendingIntent(note.id)
        // Exact alarms need a special-access grant on Android 12+; fall back to an
        // inexact alarm (usually within a few minutes) when it is not granted.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        }
    }

    fun cancel(noteId: Long) {
        alarmManager.cancel(pendingIntent(noteId))
    }

    private fun pendingIntent(noteId: Long): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setAction(ACTION_REMIND)
            .putExtra(EXTRA_NOTE_ID, noteId)
        return PendingIntent.getBroadcast(
            context,
            noteId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val ACTION_REMIND = "com.holymanzion.simplenotepro.REMIND"
        const val EXTRA_NOTE_ID = "note_id"
        const val CHANNEL_ID = "reminders"

        fun createChannel(context: Context) {
            val channel = NotificationChannel(CHANNEL_ID, "Reminders", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "Note reminders you set" }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val noteId = intent.getLongExtra(ReminderScheduler.EXTRA_NOTE_ID, -1)
        if (noteId < 0) return
        val container = (context.applicationContext as SimpleNoteApp).container
        val pending = goAsync()
        container.appScope.launch {
            try {
                // Also moves a repeating reminder on to its next time.
                container.repository.onReminderFired(noteId)?.let { showNotification(context, it) }
            } finally {
                pending.finish()
            }
        }
    }

    private fun showNotification(context: Context, note: Note) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val open = PendingIntent.getActivity(
            context,
            note.id.toInt(),
            Intent(context, MainActivity::class.java)
                .putExtra(ReminderScheduler.EXTRA_NOTE_ID, note.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val body = note.bodyText().take(300)
        val notification = NotificationCompat.Builder(context, ReminderScheduler.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(note.title.ifBlank { "Reminder" })
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(note.id.toInt(), notification)
    }
}

/** Alarms do not survive a reboot or an app update, so re-register them. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val container = (context.applicationContext as SimpleNoteApp).container
        val pending = goAsync()
        container.appScope.launch {
            try {
                container.repository.rescheduleAllReminders()
            } finally {
                pending.finish()
            }
        }
    }
}
