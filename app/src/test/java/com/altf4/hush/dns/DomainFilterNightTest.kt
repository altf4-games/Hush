package com.altf4.hush.dns

import com.altf4.hush.data.NightClock
import com.altf4.hush.data.Schedule
import java.time.ZoneOffset
import java.time.ZonedDateTime
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainFilterNightTest {
    private fun DomainFilter.blocks(name: String): Boolean {
        val bytes = name.toByteArray(Charsets.US_ASCII)
        return isBlocked(bytes, bytes.size)
    }

    private fun millis(hour: Int): Long =
        ZonedDateTime.of(2026, 11, 9, hour, 0, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli()

    private val schedule = Schedule(true, 22 * 60, 6 * 60, Schedule.ALL_DAYS)
    private val night = Blocklist.build(listOf("social.example"))
    private val always = Blocklist.build(listOf("ads.example.com"))

    private fun filter(
        hour: Int,
        allow: Blocklist = Blocklist.EMPTY,
        sched: Schedule = schedule,
        withClock: Boolean = true,
    ): DomainFilter {
        val clock = if (withClock) NightClock(sched, { ZoneOffset.UTC }, { millis(hour) }) else null
        return DomainFilter(allow, listOf(always), nightBlock = night, nightClock = clock)
    }

    @Test fun nightListBlocksOnlyInsideTheWindow() {
        assertTrue(filter(23).blocks("social.example"))
        assertTrue(filter(23).blocks("m.social.example"))
        assertFalse(filter(12).blocks("social.example"))
    }

    @Test fun alwaysListsStillApplyOutsideTheWindow() {
        assertTrue(filter(12).blocks("ads.example.com"))
        assertTrue(filter(23).blocks("ads.example.com"))
    }

    @Test fun allowedSitesWinInsideTheWindow() {
        val allow = Blocklist.build(listOf("social.example"))
        assertFalse(filter(23, allow = allow).blocks("social.example"))
    }

    @Test fun disabledScheduleNeverBlocksTheNightList() {
        assertFalse(filter(23, sched = Schedule.OFF).blocks("social.example"))
    }

    @Test fun withoutAClockTheNightListIsIgnored() {
        assertFalse(filter(23, withClock = false).blocks("social.example"))
    }

    @Test fun unrelatedNamesAreNeverBlocked() {
        assertFalse(filter(23).blocks("example.org"))
    }
}
