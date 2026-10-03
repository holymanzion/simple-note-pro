package com.holymanzion.simplenotepro.ui.components

import com.holymanzion.simplenotepro.data.Repeat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val timeFormat = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
private val dateFormat = DateTimeFormatter.ofPattern("MMM d")
private val dateYearFormat = DateTimeFormatter.ofPattern("MMM d, yyyy")
private val fullFormat = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)

/** "3:45 PM", "Yesterday", "Mar 4" or "Mar 4, 2024" depending on distance from today. */
fun friendlyTime(millis: Long): String {
    val zone = ZoneId.systemDefault()
    val dt = Instant.ofEpochMilli(millis).atZone(zone)
    val today = LocalDate.now(zone)
    val date = dt.toLocalDate()
    return when {
        date == today -> dt.format(timeFormat)
        date == today.minusDays(1) -> "Yesterday"
        date.year == today.year -> dt.format(dateFormat)
        else -> dt.format(dateYearFormat)
    }
}

/** Reminder label such as "Today, 6:00 PM" or "Tomorrow, 8:00 AM". */
fun reminderLabel(millis: Long): String {
    val zone = ZoneId.systemDefault()
    val dt = Instant.ofEpochMilli(millis).atZone(zone)
    val today = LocalDate.now(zone)
    val day = when (dt.toLocalDate()) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        today.minusDays(1) -> "Yesterday"
        else -> if (dt.year == today.year) dt.format(dateFormat) else dt.format(dateYearFormat)
    }
    return "$day, ${dt.format(timeFormat)}"
}

/** "Tomorrow, 8:00 AM · Daily" for repeating reminders. */
fun reminderLabel(millis: Long, repeat: Repeat?): String =
    if (repeat == null) reminderLabel(millis) else "${reminderLabel(millis)} · ${repeat.label}"

fun fullDateTime(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(fullFormat)
