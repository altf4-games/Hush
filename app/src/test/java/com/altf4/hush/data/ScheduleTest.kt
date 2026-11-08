package com.altf4.hush.data

import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleTest {
    // Monday 2026-11-09 is day 0 ... Sunday 2026-11-15 is day 6.
    private fun at(day: Int, hour: Int, minute: Int = 0): ZonedDateTime =
        ZonedDateTime.of(2026, 11, 9 + day, hour, minute, 0, 0, ZoneOffset.UTC)

    private val mon = 0
    private val tue = 1
    private val fri = 4
    private val sat = 5
    private val sun = 6

    private fun minutes(hour: Int, minute: Int = 0) = hour * 60 + minute

    private val nightly = Schedule(true, minutes(22), minutes(6), Schedule.ALL_DAYS)

    @Test fun sameDayWindowIncludesStartAndExcludesEnd() {
        val s = Schedule(true, minutes(9), minutes(17), Schedule.bit(DayOfWeek.MONDAY))
        assertTrue(s.isActive(at(mon, 9, 0)))
        assertTrue(s.isActive(at(mon, 16, 59)))
        assertFalse(s.isActive(at(mon, 17, 0)))
        assertFalse(s.isActive(at(mon, 8, 59)))
        assertFalse(s.isActive(at(tue, 10, 0)))
    }

    @Test fun windowRunsPastMidnight() {
        assertFalse(nightly.isActive(at(mon, 21, 59)))
        assertTrue(nightly.isActive(at(mon, 22, 0)))
        assertTrue(nightly.isActive(at(mon, 23, 59)))
        assertTrue(nightly.isActive(at(tue, 0, 0)))
        assertTrue(nightly.isActive(at(tue, 5, 59)))
        assertFalse(nightly.isActive(at(tue, 6, 0)))
        assertFalse(nightly.isActive(at(tue, 12, 0)))
    }

    @Test fun dayMaskSaysWhichDayTheWindowStarts() {
        val fridayOnly = Schedule(true, minutes(22), minutes(6), Schedule.bit(DayOfWeek.FRIDAY))
        assertFalse(fridayOnly.isActive(at(fri, 21, 0)))
        assertTrue(fridayOnly.isActive(at(fri, 23, 0)))
        assertTrue(fridayOnly.isActive(at(sat, 5, 0))) // still Friday's window
        assertFalse(fridayOnly.isActive(at(sat, 6, 0)))
        assertFalse(fridayOnly.isActive(at(sat, 23, 0))) // Saturday's window is not selected
        assertFalse(fridayOnly.isActive(at(sun, 5, 0)))
        assertFalse(fridayOnly.isActive(at(mon, 5, 0))) // Sunday is not selected either
    }

    @Test fun sundayWindowCarriesIntoMonday() {
        val sundayOnly = Schedule(true, minutes(22), minutes(6), Schedule.bit(DayOfWeek.SUNDAY))
        assertTrue(sundayOnly.isActive(at(sun, 23, 0)))
        assertTrue(sundayOnly.isActive(at(7, 3, 0))) // Monday 2026-11-16
        assertFalse(sundayOnly.isActive(at(mon, 3, 0))) // the Monday before
    }

    @Test fun neverActiveWhenOffEmptyOrZeroLength() {
        val probes = listOf(at(mon, 0), at(mon, 12), at(tue, 23), at(sun, 3))
        val off = Schedule(false, minutes(22), minutes(6), Schedule.ALL_DAYS)
        val noDays = Schedule(true, minutes(22), minutes(6), 0)
        val sameTime = Schedule(true, minutes(22), minutes(22), Schedule.ALL_DAYS)
        for (probe in probes) {
            assertFalse(off.isActive(probe))
            assertFalse(noDays.isActive(probe))
            assertFalse(sameTime.isActive(probe))
        }
        assertFalse(Schedule.OFF.isActive(at(mon, 3)))
    }

    @Test fun outOfRangeMinutesAreNotUsable() {
        assertFalse(Schedule(true, -1, minutes(6), Schedule.ALL_DAYS).isUsable)
        assertFalse(Schedule(true, minutes(22), Schedule.MINUTES_PER_DAY, Schedule.ALL_DAYS).isUsable)
        assertTrue(nightly.isUsable)
    }

    @Test fun nextChangeFindsTheComingBoundary() {
        assertEquals(at(mon, 22, 0), nightly.nextChange(at(mon, 12, 0)))
        assertEquals(at(tue, 6, 0), nightly.nextChange(at(mon, 23, 0)))
        assertEquals(at(tue, 6, 0), nightly.nextChange(at(tue, 0, 30)))
        assertEquals(at(tue, 22, 0), nightly.nextChange(at(tue, 6, 0)))
    }

    @Test fun nextChangeIsStrictlyAfterNow() {
        assertEquals(at(tue, 6, 0), nightly.nextChange(at(mon, 22, 0)))
        assertEquals(at(mon, 22, 0), nightly.nextChange(at(mon, 21, 59)))
    }

    @Test fun nextChangeSkipsUnselectedDays() {
        val fridayOnly = Schedule(true, minutes(22), minutes(6), Schedule.bit(DayOfWeek.FRIDAY))
        // Saturday morning: the Friday window is over, the next one is a week away.
        assertEquals(at(fri + 7, 22, 0), fridayOnly.nextChange(at(sat, 7, 0)))
        // Late Friday: the running window ends Saturday 06:00.
        assertEquals(at(sat, 6, 0), fridayOnly.nextChange(at(fri, 23, 0)))
    }

    @Test fun nextChangeIsNullWhenNothingEverChanges() {
        assertNull(Schedule.OFF.nextChange(at(mon, 12)))
        assertNull(Schedule(true, minutes(22), minutes(22), Schedule.ALL_DAYS).nextChange(at(mon, 12)))
        assertNull(Schedule(true, minutes(22), minutes(6), 0).nextChange(at(mon, 12)))
    }

    @Test fun nextChangeAgreesWithIsActiveAroundTheBoundary() {
        val s = Schedule(true, minutes(21, 30), minutes(7, 15), Schedule.ALL_DAYS)
        var now = at(mon, 0)
        repeat(6) {
            val next = s.nextChange(now)
            assertNotNull(next)
            val before = next!!.minusMinutes(1)
            val after = next
            assertTrue(s.isActive(before) != s.isActive(after))
            now = next
        }
    }

    @Test fun clockChangeInSpringDoesNotBreakTheWindow() {
        val zone = ZoneId.of("America/New_York")
        // 02:30 does not exist on 2026-03-08 in New York; the boundary moves to 03:30.
        val s = Schedule(true, minutes(2, 30), minutes(6), Schedule.ALL_DAYS)
        val now = ZonedDateTime.of(2026, 3, 8, 0, 0, 0, 0, zone)
        val next = s.nextChange(now)
        assertNotNull(next)
        assertEquals(LocalTime.of(3, 30), next!!.toLocalTime())
        // An ordinary overnight window still ends on time that morning.
        val overnight = Schedule(true, minutes(22), minutes(7), Schedule.ALL_DAYS)
        val evening = ZonedDateTime.of(2026, 3, 7, 23, 0, 0, 0, zone)
        assertEquals(LocalTime.of(7, 0), overnight.nextChange(evening)!!.toLocalTime())
        assertTrue(overnight.isActive(evening))
    }
}
