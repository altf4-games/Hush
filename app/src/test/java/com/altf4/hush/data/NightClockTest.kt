package com.altf4.hush.data

import java.time.ZoneOffset
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NightClockTest {
    private fun millis(day: Int, hour: Int, minute: Int = 0): Long =
        ZonedDateTime.of(2026, 11, 9 + day, hour, minute, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli()

    private val nightly = Schedule(true, 22 * 60, 6 * 60, Schedule.ALL_DAYS)

    private class Fixture(schedule: Schedule, start: Long) {
        var time = start
        var zoneCalls = 0
        val clock = NightClock(
            schedule,
            zone = {
                zoneCalls++
                ZoneOffset.UTC
            },
            now = { time },
        )
    }

    @Test fun followsTheWindow() {
        val f = Fixture(nightly, millis(0, 12))
        assertFalse(f.clock.isActive())
        f.time = millis(0, 22, 0)
        assertTrue(f.clock.isActive())
        f.time = millis(1, 5, 59)
        assertTrue(f.clock.isActive())
        f.time = millis(1, 6, 0)
        assertFalse(f.clock.isActive())
    }

    @Test fun answerIsCachedUntilTheRecheckPoint() {
        val f = Fixture(nightly, millis(0, 12))
        f.clock.isActive()
        assertEquals(1, f.zoneCalls)
        f.time += 60_000
        f.time += 60_000
        f.clock.isActive()
        f.clock.isActive()
        assertEquals(1, f.zoneCalls)
        f.time = millis(0, 12) + NightClock.RECHECK_MS
        f.clock.isActive()
        assertEquals(2, f.zoneCalls)
    }

    @Test fun switchesRightAtTheBoundaryEvenInsideTheCachedSpan() {
        val f = Fixture(nightly, millis(0, 21, 55))
        assertFalse(f.clock.isActive()) // valid until 22:00, which is closer than the cap
        f.time = millis(0, 21, 59)
        assertFalse(f.clock.isActive())
        f.time = millis(0, 22, 0)
        assertTrue(f.clock.isActive())
    }

    @Test fun unusableScheduleNeverTouchesTheClock() {
        val f = Fixture(Schedule.OFF, millis(0, 23))
        repeat(3) { assertFalse(f.clock.isActive()) }
        assertEquals(0, f.zoneCalls)
    }
}
