package com.altf4.hush.block

import com.altf4.hush.block.EmergencyPass.Phase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmergencyPassTest {
    private val t0 = 1_000_000L
    private val app = "com.example.social"
    private val other = "com.example.video"

    @Test fun startsWithNoRequest() {
        val pass = EmergencyPass()
        assertEquals(Phase.CanRequest, pass.phase(app, false, t0))
        assertEquals(0L, pass.waitLeft(app, t0))
        assertFalse(pass.isActive(app, t0))
    }

    @Test fun waitThenReadyThenGrant() {
        val pass = EmergencyPass()
        pass.request(app, false, t0)
        assertEquals(Phase.Waiting, pass.phase(app, false, t0))
        assertEquals(40_000L, pass.waitLeft(app, t0 + 20_000))
        assertFalse(pass.grant(app, false, t0 + 59_999))
        assertEquals(Phase.Ready, pass.phase(app, false, t0 + 60_000))
        assertTrue(pass.grant(app, false, t0 + 60_000))
    }

    @Test fun passLastsFiveMinutesForThatAppOnly() {
        val pass = EmergencyPass()
        pass.request(app, false, t0)
        val granted = t0 + 61_000
        pass.grant(app, false, granted)
        assertTrue(pass.isActive(app, granted))
        assertTrue(pass.isActive(app, granted + EmergencyPass.PASS_MS - 1))
        assertFalse(pass.isActive(app, granted + EmergencyPass.PASS_MS))
        assertFalse(pass.isActive(other, granted))
    }

    @Test fun grantUsesUpTheRequest() {
        val pass = EmergencyPass()
        pass.request(app, false, t0)
        assertTrue(pass.grant(app, false, t0 + 60_000))
        assertFalse(pass.grant(app, false, t0 + 61_000))
        assertEquals(Phase.CanRequest, pass.phase(app, false, t0 + 61_000))
    }

    @Test fun askingAgainDoesNotRestartTheWait() {
        val pass = EmergencyPass()
        pass.request(app, false, t0)
        pass.request(app, false, t0 + 30_000)
        assertEquals(Phase.Ready, pass.phase(app, false, t0 + 60_000))
    }

    @Test fun anUnusedRequestLapses() {
        val pass = EmergencyPass()
        pass.request(app, false, t0)
        val late = t0 + EmergencyPass.REQUEST_TTL_MS
        assertEquals(Phase.CanRequest, pass.phase(app, false, late))
        assertFalse(pass.grant(app, false, late))
        pass.request(app, false, late)
        assertEquals(Phase.Waiting, pass.phase(app, false, late))
    }

    @Test fun aRequestIsForOneAppAtATime() {
        val pass = EmergencyPass()
        pass.request(app, false, t0)
        assertEquals(Phase.CanRequest, pass.phase(other, false, t0 + 60_000))
        assertFalse(pass.grant(other, false, t0 + 60_000))
        pass.request(other, false, t0 + 10_000)
        assertEquals(Phase.CanRequest, pass.phase(app, false, t0 + 70_000))
        assertEquals(Phase.Ready, pass.phase(other, false, t0 + 70_000))
    }

    @Test fun strictModeRefusesEverything() {
        val pass = EmergencyPass()
        pass.request(app, true, t0)
        assertEquals(Phase.Unavailable, pass.phase(app, true, t0 + 120_000))
        assertFalse(pass.grant(app, true, t0 + 120_000))
        // Nothing was recorded, so turning strict mode off later starts from scratch.
        assertEquals(Phase.CanRequest, pass.phase(app, false, t0 + 120_000))
    }

    @Test fun clearDropsRequestsAndPasses() {
        val pass = EmergencyPass()
        pass.request(app, false, t0)
        pass.grant(app, false, t0 + 60_000)
        pass.request(other, false, t0 + 61_000)
        pass.clear()
        assertFalse(pass.isActive(app, t0 + 62_000))
        assertEquals(Phase.CanRequest, pass.phase(other, false, t0 + 130_000))
    }
}
