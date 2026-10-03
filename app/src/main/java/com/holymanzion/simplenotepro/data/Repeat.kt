package com.holymanzion.simplenotepro.data

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

enum class Repeat(val label: String) {
    DAILY("Daily"),
    WEEKLY("Weekly"),
    MONTHLY("Monthly"),
    YEARLY("Yearly");

    private fun ZonedDateTime.step(): ZonedDateTime = when (this@Repeat) {
        DAILY -> plusDays(1)
        WEEKLY -> plusWeeks(1)
        MONTHLY -> plusMonths(1)
        YEARLY -> plusYears(1)
    }

    /**
     * The first occurrence after [now], stepping from [from] in local calendar time, so a
     * daily 8:00 reminder stays at 8:00 across daylight-saving changes. Monthly steps clamp
     * to the month's last day (Jan 31 → Feb 28 → Mar 28), as java.time does.
     */
    fun nextAfter(from: Long, now: Long, zone: ZoneId = ZoneId.systemDefault()): Long {
        var next = Instant.ofEpochMilli(from).atZone(zone)
        do next = next.step() while (next.toInstant().toEpochMilli() <= now)
        return next.toInstant().toEpochMilli()
    }

    companion object {
        fun fromName(name: String?): Repeat? = entries.firstOrNull { it.name == name }
    }
}
