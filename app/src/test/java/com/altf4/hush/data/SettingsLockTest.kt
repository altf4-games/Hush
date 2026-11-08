package com.altf4.hush.data

import java.time.ZoneOffset
import java.time.ZonedDateTime
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsLockTest {
    private fun at(hour: Int): ZonedDateTime =
        ZonedDateTime.of(2026, 11, 9, hour, 0, 0, 0, ZoneOffset.UTC)

    private val nightly = Schedule(true, 22 * 60, 6 * 60, Schedule.ALL_DAYS)

    @Test fun lockedOnlyWhileTheWindowIsActive() {
        assertTrue(SettingsLock.isLocked(nightly, at(23)))
        assertFalse(SettingsLock.isLocked(nightly, at(12)))
        assertFalse(SettingsLock.isLocked(Schedule.OFF, at(23)))
    }

    @Test fun scheduleIsFrozenWhileLocked() {
        assertFalse(SettingsLock.allowsScheduleChange(locked = true))
        assertTrue(SettingsLock.allowsScheduleChange(locked = false))
    }

    @Test fun nightSwitchCannotBeTurnedOffWhileLocked() {
        assertFalse(SettingsLock.allowsNightSwitch(locked = true, turnOn = false))
        assertTrue(SettingsLock.allowsNightSwitch(locked = true, turnOn = true))
        assertTrue(SettingsLock.allowsNightSwitch(locked = false, turnOn = false))
    }

    @Test fun strictModeCannotBeTurnedOffWhileLocked() {
        assertFalse(SettingsLock.allowsStrictChange(locked = true, turnOn = false))
        assertTrue(SettingsLock.allowsStrictChange(locked = true, turnOn = true))
        assertTrue(SettingsLock.allowsStrictChange(locked = false, turnOn = false))
    }

    @Test fun appsCanBeAddedButNotRemovedWhileLocked() {
        val current = setOf("a", "b")
        assertTrue(SettingsLock.allowsAppChange(true, current, setOf("a", "b", "c")))
        assertTrue(SettingsLock.allowsAppChange(true, current, current))
        assertFalse(SettingsLock.allowsAppChange(true, current, setOf("a")))
        assertFalse(SettingsLock.allowsAppChange(true, current, emptySet()))
        // A swap removes one, so it is refused too.
        assertFalse(SettingsLock.allowsAppChange(true, current, setOf("a", "c")))
        assertTrue(SettingsLock.allowsAppChange(false, current, emptySet()))
    }

    @Test fun socialPresetsOnlyGetStricterWhileLocked() {
        val off = SocialMode.Off
        val night = SocialMode.Night
        val always = SocialMode.Always
        assertTrue(SettingsLock.allowsSocialChange(true, off, night))
        assertTrue(SettingsLock.allowsSocialChange(true, off, always))
        assertTrue(SettingsLock.allowsSocialChange(true, night, always))
        assertTrue(SettingsLock.allowsSocialChange(true, night, night))
        assertFalse(SettingsLock.allowsSocialChange(true, night, off))
        assertFalse(SettingsLock.allowsSocialChange(true, always, night))
        assertFalse(SettingsLock.allowsSocialChange(true, always, off))
        for (from in SocialMode.entries) {
            for (to in SocialMode.entries) {
                assertTrue(SettingsLock.allowsSocialChange(false, from, to))
            }
        }
    }
}
