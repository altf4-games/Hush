package com.altf4.hush.block

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EssentialAppsTest {
    @Test fun knownEssentialsAreRecognised() {
        assertTrue(EssentialApps.isEssential("com.android.settings", emptySet()))
        assertTrue(EssentialApps.isEssential("com.google.android.deskclock", emptySet()))
        assertTrue(EssentialApps.isEssential("com.google.android.apps.maps", emptySet()))
    }

    @Test fun deviceDefaultsAreEssentialToo() {
        assertFalse(EssentialApps.isEssential("com.vendor.messages", emptySet()))
        assertTrue(EssentialApps.isEssential("com.vendor.messages", setOf("com.vendor.messages")))
    }

    @Test fun ordinaryAppsAreNot() {
        assertFalse(EssentialApps.isEssential("com.example.game", setOf("com.vendor.messages")))
    }
}
