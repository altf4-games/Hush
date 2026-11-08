package com.altf4.hush.data

import java.time.ZonedDateTime

/**
 * The rules for what may change while the quiet window is running. Pure functions, so they are
 * easy to test; [Prefs] applies them to every setter, so the screens cannot get around them.
 *
 * While the window is active, settings can only get stricter: the schedule is frozen, apps and
 * quiet-hours sites can be added but not removed, and strict mode can be turned on but not off.
 * Outside the window everything can be changed freely.
 *
 * Limit: this trusts the device clock. Changing the system time is not guarded against.
 */
object SettingsLock {
    fun isLocked(schedule: Schedule, now: ZonedDateTime): Boolean = schedule.isActive(now)

    /** Window times and weekdays. */
    fun allowsScheduleChange(locked: Boolean): Boolean = !locked

    /** The master switch: it can be turned on at any time, but off only outside the window. */
    fun allowsNightSwitch(locked: Boolean, turnOn: Boolean): Boolean = !locked || turnOn

    fun allowsStrictChange(locked: Boolean, turnOn: Boolean): Boolean = !locked || turnOn

    /** The chosen apps may grow while locked, never shrink. */
    fun allowsAppChange(locked: Boolean, current: Set<String>, next: Set<String>): Boolean =
        !locked || next.containsAll(current)

    /** A social preset may move towards "blocked more" while locked (Off < Night < Always). */
    fun allowsSocialChange(locked: Boolean, from: SocialMode, to: SocialMode): Boolean =
        !locked || strictness(to) >= strictness(from)

    private fun strictness(mode: SocialMode): Int = when (mode) {
        SocialMode.Off -> 0
        SocialMode.Night -> 1
        SocialMode.Always -> 2
    }
}
