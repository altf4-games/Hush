package com.altf4.hush.data

import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * The quiet-hours window. It starts at [startMinute] and ends at [endMinute] (minutes after
 * midnight); if the end is earlier than the start the window runs past midnight into the next
 * day. [days] says on which days the window *starts* (bit 0 = Monday ... bit 6 = Sunday), so a
 * Friday window that runs to Saturday 06:00 belongs to Friday.
 *
 * A schedule that is off, has no days, or has the same start and end never becomes active.
 * Pure functions of the time passed in: nothing here reads the clock or sets a timer.
 */
class Schedule(
    val enabled: Boolean,
    val startMinute: Int,
    val endMinute: Int,
    val days: Int,
) {
    /** False when the schedule can never be active. */
    val isUsable: Boolean
        get() = enabled &&
            startMinute in 0 until MINUTES_PER_DAY &&
            endMinute in 0 until MINUTES_PER_DAY &&
            startMinute != endMinute &&
            (days and ALL_DAYS) != 0

    private val crossesMidnight: Boolean get() = endMinute < startMinute

    private fun hasDay(day: DayOfWeek): Boolean = (days and bit(day)) != 0

    fun isActive(now: ZonedDateTime): Boolean {
        if (!isUsable) return false
        val minute = now.hour * 60 + now.minute
        val today = hasDay(now.dayOfWeek)
        return if (!crossesMidnight) {
            today && minute >= startMinute && minute < endMinute
        } else {
            (today && minute >= startMinute) ||
                (hasDay(now.dayOfWeek.minus(1)) && minute < endMinute)
        }
    }

    /**
     * The next moment the schedule switches between active and inactive, strictly after [now], or
     * null if it never does. Windows never overlap, so every boundary is a switch.
     */
    fun nextChange(now: ZonedDateTime): ZonedDateTime? {
        if (!isUsable) return null
        val zone = now.zone
        val startTime = LocalTime.of(startMinute / 60, startMinute % 60)
        val endTime = LocalTime.of(endMinute / 60, endMinute % 60)
        var best: ZonedDateTime? = null
        // A window that began yesterday may still be running; look a week ahead for the next one.
        for (offset in -1L..8L) {
            val date = now.toLocalDate().plusDays(offset)
            if (!hasDay(date.dayOfWeek)) continue
            val start = ZonedDateTime.of(date, startTime, zone)
            val end = ZonedDateTime.of(if (crossesMidnight) date.plusDays(1) else date, endTime, zone)
            for (boundary in arrayOf(start, end)) {
                val current = best
                if (boundary.isAfter(now) && (current == null || boundary.isBefore(current))) {
                    best = boundary
                }
            }
        }
        return best
    }

    companion object {
        const val MINUTES_PER_DAY = 24 * 60
        const val ALL_DAYS = 0x7F

        val OFF = Schedule(enabled = false, startMinute = 0, endMinute = 0, days = 0)

        fun bit(day: DayOfWeek): Int = 1 shl (day.value - 1)
    }
}
