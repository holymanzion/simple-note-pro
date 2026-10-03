package com.holymanzion.simplenotepro

import com.holymanzion.simplenotepro.data.Repeat
import com.holymanzion.simplenotepro.lock.AppLock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class RepeatAndLockTest {
    private val zone = ZoneId.of("Europe/London")
    private fun at(text: String) = LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()
    private fun local(millis: Long) = java.time.Instant.ofEpochMilli(millis).atZone(zone).toLocalDateTime().toString()

    @Test fun dailyGoesToNextDay() {
        assertEquals("2026-03-11T08:00", local(Repeat.DAILY.nextAfter(at("2026-03-10T08:00"), at("2026-03-10T08:00"), zone)))
    }

    @Test fun dailyKeepsWallClockTimeAcrossDaylightSaving() {
        // Clocks go forward in the UK on 29 March 2026; 8:00 must stay 8:00.
        assertEquals("2026-03-30T08:00", local(Repeat.DAILY.nextAfter(at("2026-03-29T08:00"), at("2026-03-29T09:00"), zone)))
    }

    @Test fun missedOccurrencesAreSkipped() {
        // Phone was off for a week: the next one is tomorrow, not seven catch-up alarms.
        assertEquals("2026-05-09T07:30", local(Repeat.DAILY.nextAfter(at("2026-05-01T07:30"), at("2026-05-08T12:00"), zone)))
    }

    @Test fun weeklyMonthlyYearly() {
        val from = at("2026-01-31T09:00")
        assertEquals("2026-02-07T09:00", local(Repeat.WEEKLY.nextAfter(from, from, zone)))
        assertEquals("2026-02-28T09:00", local(Repeat.MONTHLY.nextAfter(from, from, zone)))
        assertEquals("2027-01-31T09:00", local(Repeat.YEARLY.nextAfter(from, from, zone)))
    }

    @Test fun repeatNamesRoundTrip() {
        Repeat.entries.forEach { assertEquals(it, Repeat.fromName(it.name)) }
        assertEquals(null, Repeat.fromName(null))
        assertEquals(null, Repeat.fromName("HOURLY"))
    }

    // --- App lock timing ---

    @Test fun locksAtLaunchWhenEnabled() {
        assertTrue(AppLock({ true }).locked.value)
        assertFalse(AppLock({ false }).locked.value)
    }

    @Test fun quickSwitchDoesNotRelock() {
        val lock = AppLock({ true }, gracePeriodMs = 60_000)
        lock.unlock()
        lock.onBackground(now = 0)
        lock.onForeground(now = 59_999)
        assertFalse(lock.locked.value)
    }

    @Test fun longAbsenceRelocks() {
        val lock = AppLock({ true }, gracePeriodMs = 60_000)
        lock.unlock()
        lock.onBackground(now = 0)
        lock.onForeground(now = 60_000)
        assertTrue(lock.locked.value)
    }

    @Test fun neverLocksWhenDisabled() {
        val lock = AppLock({ false }, gracePeriodMs = 0)
        lock.onBackground(now = 0)
        lock.onForeground(now = 1_000_000)
        lock.lockNow()
        assertFalse(lock.locked.value)
    }
}
