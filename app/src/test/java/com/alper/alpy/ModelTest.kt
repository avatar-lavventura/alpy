package com.alper.alpy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

class ModelTest {
    private val z = ZoneOffset.UTC
    private fun ms(s: String) = LocalDateTime.parse(s).toInstant(z).toEpochMilli()
    private fun Reminder.after(s: String) = occurrenceAfter(ms(s), z)?.let { LocalDateTime.ofEpochSecond(it / 1000, 0, z).toString() }

    @Test fun once() {
        val r = Reminder(start = ms("2026-10-14T09:00"))
        assertEquals("2026-10-14T09:00", r.after("2026-10-14T08:00"))
        assertNull(r.after("2026-10-14T09:00"))
    }

    @Test fun weekdaysOnly() {
        // 2026-10-16 is a Friday
        val r = Reminder(start = ms("2026-10-16T07:30"), rule = Every.DAYS, weekdays = (1..5).toSet())
        assertEquals("2026-10-19T07:30", r.after("2026-10-16T08:00"))
    }

    @Test fun monthEndClamps() {
        val r = Reminder(start = ms("2026-01-31T10:00"), rule = Every.MONTHS)
        assertEquals("2026-02-28T10:00", r.after("2026-02-01T00:00"))
        assertEquals("2026-03-31T10:00", r.after("2026-03-01T00:00"))
    }

    @Test fun everyTwoHoursFromAnchor() {
        val r = Reminder(start = ms("2026-10-14T09:15"), rule = Every.HOURS, every = 2)
        assertEquals("2026-10-15T01:15", r.after("2026-10-14T23:20"))
    }

    @Test fun dayMinderWindow() {
        val r = Reminder(start = ms("2026-10-14T00:00"), rule = Every.MINUTES, every = 90, windowFrom = 9 * 60, windowTo = 21 * 60)
        assertEquals("2026-10-14T09:00", r.after("2026-10-14T03:00"))
        assertEquals("2026-10-14T21:00", r.after("2026-10-14T19:30"))
        assertEquals("2026-10-15T09:00", r.after("2026-10-14T21:00"))
    }

    @Test fun nagUntilLimitThenStops() {
        val start = ms("2026-10-14T09:00")
        var r = Reminder(start = start, nag = true, nagEveryMin = 5, nagTimes = 2).rescheduled(start - 1000)
        assertEquals(start, r.fireAt())

        var (x, ev) = r.fire(start, z)
        assertEquals(Fire.MAIN, ev); r = x
        assertEquals(start + 5 * 60_000, r.fireAt())

        r.fire(r.fireAt()!!, z).let { x = it.first; ev = it.second }
        assertEquals(Fire.NAG, ev); r = x
        r.fire(r.fireAt()!!, z).let { x = it.first; ev = it.second }
        assertEquals(Fire.NAG, ev); r = x
        assertNull(r.fireAt())
        assertFalse(r.done)
        assertTrue(r.done().done)
    }

    @Test fun checkSkipsRepeatOrFinishesOnce() {
        val start = ms("2026-10-14T09:00")
        val daily = Reminder(start = start, rule = Every.DAYS, nextAt = start)
        assertEquals(ms("2026-10-15T09:00"), daily.check(z).nextAt)
        assertTrue(Reminder(start = start, nextAt = start).check(z).done)
    }

    @Test fun adjustOverduePushesFromNow() {
        val r = Reminder(start = ms("2026-10-14T09:00"))
        assertEquals(ms("2026-10-14T10:05"), r.adjust(5 * 60_000, ms("2026-10-14T10:00:30")).nextAt)
        assertEquals(ms("2026-10-14T08:00"), r.adjust(-60 * 60_000, ms("2026-10-14T10:00")).start)
    }

    @Test fun preAlarmThenMain() {
        val start = ms("2026-10-14T09:00")
        var r = Reminder(start = start, rule = Every.DAYS, preMin = 15).rescheduled(start - 3_600_000)
        assertEquals(start - 15 * 60_000, r.fireAt())
        r = r.fire(r.fireAt()!!, z).also { assertEquals(Fire.PRE, it.second) }.first
        r = r.fire(r.fireAt()!!, z).also { assertEquals(Fire.MAIN, it.second) }.first
        assertEquals(ms("2026-10-15T08:45"), r.fireAt())
    }
}
