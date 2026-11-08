package com.altf4.hush.block

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockDecisionTest {
    private val own = "com.altf4.hush"
    private val chosen = setOf("com.example.social", "com.example.video")

    @Test fun blocksChosenAppWhileActive() {
        assertTrue(BlockDecision.shouldBlock("com.example.social", own, chosen) { true })
    }

    @Test fun allowsChosenAppWhenWindowIsNotActive() {
        assertFalse(BlockDecision.shouldBlock("com.example.social", own, chosen) { false })
    }

    @Test fun allowsAppsThatWereNotChosen() {
        assertFalse(BlockDecision.shouldBlock("com.example.maps", own, chosen) { true })
    }

    @Test fun neverBlocksItself() {
        assertFalse(BlockDecision.shouldBlock(own, own, chosen + own) { true })
    }

    @Test fun ignoresEventsWithoutAPackage() {
        assertFalse(BlockDecision.shouldBlock(null, own, chosen) { true })
    }

    @Test fun onlyAsksTheClockForChosenApps() {
        var asked = 0
        BlockDecision.shouldBlock("com.example.maps", own, chosen) { asked++; true }
        BlockDecision.shouldBlock(null, own, chosen) { asked++; true }
        BlockDecision.shouldBlock(own, own, chosen + own) { asked++; true }
        assertEquals(0, asked)
        BlockDecision.shouldBlock("com.example.video", own, chosen) { asked++; true }
        assertEquals(1, asked)
    }
}
