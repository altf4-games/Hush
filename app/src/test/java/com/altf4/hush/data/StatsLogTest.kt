package com.altf4.hush.data

import org.junit.Assert.assertEquals
import org.junit.Test

class StatsLogTest {
    @Test fun countsTotalAndBlockedPerDay() {
        val log = StatsLog()
        log.record(100, blocked = false)
        log.record(100, blocked = true)
        log.record(101, blocked = true)
        assertEquals(2, log.on(100).total)
        assertEquals(1, log.on(100).blocked)
        assertEquals(1, log.on(101).total)
        assertEquals(3L, log.lifetimeTotal)
        assertEquals(2L, log.lifetimeBlocked)
    }

    @Test fun lastFillsMissingDaysWithZeroOldestFirst() {
        val log = StatsLog()
        log.record(10, blocked = true)
        log.record(12, blocked = false)
        val week = log.last(12, 3)
        assertEquals(listOf(10, 11, 12), week.map { it.day })
        assertEquals(listOf(1, 0, 1), week.map { it.total })
    }

    @Test fun onlyTheLastSevenDaysAreKeptButLifetimeSurvives() {
        val log = StatsLog()
        for (d in 1..10) log.record(d, blocked = true)
        assertEquals(0, log.on(1).total)
        assertEquals(1, log.on(10).total)
        assertEquals(10L, log.lifetimeTotal)
    }

    @Test fun roundTripsThroughText() {
        val log = StatsLog()
        log.record(5, blocked = true)
        log.record(5, blocked = false)
        log.record(6, blocked = false)
        val copy = StatsLog.decode(log.encode())
        assertEquals(log.encode(), copy.encode())
        assertEquals(3L, copy.lifetimeTotal)
        assertEquals(1, copy.on(5).blocked)
    }

    @Test fun malformedTextGivesAnEmptyLog() {
        for (bad in listOf(null, "", "x", "1|2", "a|b|c", "5|9|1:2:3", "10|2|1:1:5", "-1|0|", "5|1|1:2")) {
            val log = StatsLog.decode(bad)
            assertEquals(bad, 0L, log.lifetimeTotal)
            assertEquals(bad, 0, log.on(1).total)
        }
    }

    @Test fun dayOfFollowsTheLocalOffset() {
        val noonUtc = 12L * 3_600_000
        assertEquals(0, StatsLog.dayOf(noonUtc, 0))
        assertEquals(1, StatsLog.dayOf(noonUtc, 13 * 3_600_000)) // already tomorrow there
        assertEquals(-1, StatsLog.dayOf(-1, 0))
    }

    @Test fun clearEmptiesEverything() {
        val log = StatsLog()
        log.record(1, blocked = true)
        log.clear()
        assertEquals(0L, log.lifetimeTotal)
        assertEquals(0, log.on(1).total)
    }
}
