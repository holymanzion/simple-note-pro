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
import java.util.concurrent.TimeUnit

class ReminderScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    /** The note's own reminder, at [Note.reminderAt]. */
    fun schedule(note: Note) {
        val at = note.reminderAt ?: return
        setAlarm(at, alarmIntent(note.id, ACTION_REMIND))
    }

    /**
     * An extra one-off alert for a snoozed *repeating* reminder, kept separate so the
     * repeat schedule itself doesn't move.
     */
    fun scheduleSnooze(noteId: Long, at: Long) = setAlarm(at, alarmIntent(noteId, ACTION_SNOOZED))

    /** Cancels the reminder and any snooze. */
    fun cancel(noteId: Long) {
        cancelIntent(alarmIntent(noteId, ACTION_REMIND))
        cancelSnooze(noteId)
    }

    fun cancelSnooze(noteId: Long) = cancelIntent(alarmIntent(noteId, ACTION_SNOOZED))

    /** For tests: whether an alarm of each kind is waiting for [noteId]. */
    fun isScheduled(noteId: Long) = existing(noteId, ACTION_REMIND)
    fun isSnoozed(noteId: Long) = existing(noteId, ACTION_SNOOZED)

    private fun setAlarm(at: Long, pending: PendingIntent) {
        // Exact alarms need a special-access grant on Android 12+; fall back to an
        // inexact alarm (usually within a few minutes) when it is not granted.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        }
    }

    /** Cancelling the PendingIntent too (not just the alarm) lets [existing] report it gone. */
    private fun cancelIntent(pending: PendingIntent) {
        alarmManager.cancel(pending)
        pending.cancel()
    }

    private fun intent(noteId: Long, action: String) =
        Intent(context, ReminderReceiver::class.java).setAction(action).putExtra(EXTRA_NOTE_ID, noteId)

    private fun alarmIntent(noteId: Long, action: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        noteId.toInt(),
        intent(noteId, action), // the action keeps reminder and snooze alarms apart
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun existing(noteId: Long, action: String) = PendingIntent.getBroadcast(
        context,
        noteId.toInt(),
        intent(noteId, action),
        PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
    ) != null

    companion object {
        const val ACTION_REMIND = "com.holymanzion.simplenotepro.REMIND"
        const val ACTION_SNOOZED = "com.holymanzion.simplenotepro.SNOOZED"
        const val EXTRA_NOTE_ID = "note_id"
        const val CHANNEL_ID = "reminders"
        val SNOOZE_MS = TimeUnit.HOURS.toMillis(1)

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
        val repository = (context.applicationContext as SimpleNoteApp).container.repository
        val pending = goAsync()
        (context.applicationContext as SimpleNoteApp).container.appScope.launch {
            try {
                val note = when (intent.action) {
                    // A snooze alert just shows the note again; the schedule stays as it is.
                    ReminderScheduler.ACTION_SNOOZED -> repository.get(noteId)?.note?.takeIf { !it.isTrashed }
                    // Also moves a repeating reminder on to its next time.
                    else -> repository.onReminderFired(noteId)
                }
                note?.let { showReminderNotification(context, it) }
            } finally {
                pending.finish()
            }
        }
    }
}

/** "Snooze 1 hour" and "Done" on the reminder notification. */
class ReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val noteId = intent.getLongExtra(ReminderScheduler.EXTRA_NOTE_ID, -1)
        if (noteId < 0) return
        NotificationManagerCompat.from(context).cancel(noteId.toInt())
        val container = (context.applicationContext as SimpleNoteApp).container
        val pending = goAsync()
        container.appScope.launch {
            try {
                when (intent.action) {
                    ACTION_SNOOZE -> container.repository.snoozeReminder(noteId, System.currentTimeMillis() + ReminderScheduler.SNOOZE_MS)
                    ACTION_DONE -> container.repository.completeReminder(noteId)
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_SNOOZE = "com.holymanzion.simplenotepro.reminder.SNOOZE"
        const val ACTION_DONE = "com.holymanzion.simplenotepro.reminder.DONE"

        fun intent(context: Context, noteId: Long, action: String): PendingIntent = PendingIntent.getBroadcast(
            context,
            noteId.toInt(),
            Intent(context, ReminderActionReceiver::class.java).setAction(action).putExtra(ReminderScheduler.EXTRA_NOTE_ID, noteId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

private fun showReminderNotification(context: Context, note: Note) {
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
        .addAction(R.drawable.ic_snooze, "Snooze 1 hour", ReminderActionReceiver.intent(context, note.id, ReminderActionReceiver.ACTION_SNOOZE))
        .addAction(R.drawable.ic_done, "Done", ReminderActionReceiver.intent(context, note.id, ReminderActionReceiver.ACTION_DONE))
        .build()
    NotificationManagerCompat.from(context).notify(note.id.toInt(), notification)
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
