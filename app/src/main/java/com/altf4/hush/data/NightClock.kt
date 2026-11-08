package com.altf4.hush.data

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Answers "is the quiet window active right now?" cheaply enough to ask on every DNS lookup or
 * window change. It remembers the answer together with the moment it stops being valid (the next
 * switch, but never more than [RECHECK_MS] ahead so a time-zone or clock change is picked up) and
 * only recomputes when a query arrives after that moment. No timers, no polling.
 */
class NightClock(
    private val schedule: Schedule,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    @Volatile private var active = false
    @Volatile private var validUntil = Long.MIN_VALUE

    fun isActive(): Boolean {
        if (!schedule.isUsable) return false
        val t = now()
        if (t >= validUntil) refresh(t)
        return active
    }

    private fun refresh(t: Long) {
        val moment = ZonedDateTime.ofInstant(Instant.ofEpochMilli(t), zone())
        val cap = t + RECHECK_MS
        val next = schedule.nextChange(moment)?.toInstant()?.toEpochMilli() ?: cap
        // Write the answer first: a reader that sees the new expiry must also see the new answer.
        active = schedule.isActive(moment)
        validUntil = minOf(next, cap)
    }

    companion object {
        const val RECHECK_MS = 15L * 60 * 1000
    }
}
