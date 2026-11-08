package com.altf4.hush.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeModeTest {
    @Test fun darkIsTheDefault() {
        assertEquals(ThemeMode.Dark, ThemeMode.fromKey(null))
        assertEquals(ThemeMode.Dark, ThemeMode.fromKey(""))
        assertEquals(ThemeMode.Dark, ThemeMode.fromKey("nonsense"))
    }

    @Test fun keysRoundTrip() {
        for (mode in ThemeMode.entries) {
            assertEquals(mode, ThemeMode.fromKey(mode.key))
        }
    }
}
