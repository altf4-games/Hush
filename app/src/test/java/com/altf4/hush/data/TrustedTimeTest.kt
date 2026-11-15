package com.altf4.hush.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrustedTimeTest {
    private class MemoryStore(var anchor: TimeAnchor? = null) : TimeStore {
        override fun load() = anchor
        override fun save(anchor: TimeAnchor) {
            this.anchor = anchor
        }
    }

    private var wall = 1_000_000L
    private var elapsed = 500L
    private var boot = 7
    private val store = MemoryStore()

    private fun clock() = TrustedClock({ wall }, { elapsed }, { boot }, store)

    @Test fun usesThePhoneClockUntilTheFirstSync() {
        val c = clock()
        assertEquals(wall, c.now())
        assertFalse(c.synced)
        assertEquals(Long.MAX_VALUE, c.ageMs())
    }

    @Test fun changingThePhoneClockAfterASyncHasNoEffect() {
        val c = clock()
        c.record(serverMs = 5_000_000L)
        elapsed += 60_000
        wall += 60_000
        assertEquals(5_060_000L, c.now())
        wall -= 10_000_000L
        assertEquals(5_060_000L, c.now())
        wall += 99_000_000_000L
        assertEquals(5_060_000L, c.now())
        assertTrue(c.synced)
        assertEquals(60_000L, c.ageMs())
    }

    @Test fun theAnchorSurvivesARestartOfTheProcess() {
        clock().record(5_000_000L)
        elapsed += 1_000
        wall = 42
        assertEquals(5_001_000L, clock().now())
    }

    @Test fun afterARebootTheLastSkewIsUsedUntilTheNextSync() {
        val c = clock()
        c.record(5_000_000L)
        boot = 8
        elapsed = 100
        wall = 2_000_000L
        val rebooted = clock()
        assertEquals(2_000_000L + 4_000_000L, rebooted.now())
        assertFalse(rebooted.synced)
        assertEquals(Long.MAX_VALUE, rebooted.ageMs())
        rebooted.record(6_500_000L)
        assertTrue(rebooted.synced)
        assertEquals(6_500_000L, rebooted.now())
    }

    @Test fun anUnknownBootCountNeverTrustsTheElapsedAnchor() {
        boot = -1
        val c = clock()
        c.record(5_000_000L)
        wall += 10_000
        elapsed += 10_000
        assertFalse(c.synced)
        assertEquals(wall + 4_000_000L, c.now())
    }

    private val nonce = ByteArray(8) { (it + 1).toByte() }

    private fun reply(unixSeconds: Long, fractionMs: Int = 0, mode: Int = 4, stratum: Int = 2, li: Int = 0, echo: ByteArray = nonce): ByteArray {
        val r = ByteArray(48)
        r[0] = ((li shl 6) or (4 shl 3) or mode).toByte()
        r[1] = stratum.toByte()
        System.arraycopy(echo, 0, r, 24, 8)
        val ntp = unixSeconds + 2_208_988_800L
        for (i in 0 until 4) r[40 + i] = (ntp shr (24 - 8 * i)).toByte()
        val fraction = (fractionMs.toLong() shl 32) / 1000
        for (i in 0 until 4) r[44 + i] = (fraction shr (24 - 8 * i)).toByte()
        return r
    }

    @Test fun requestCarriesTheNonceAndClientMode() {
        val q = Sntp.request(nonce)
        assertEquals(48, q.size)
        assertEquals(0x1B, q[0].toInt())
        assertEquals(nonce.toList(), q.copyOfRange(40, 48).toList())
    }

    @Test fun parsesAReplyAndAddsHalfTheRoundTrip() {
        val r = reply(1_800_000_000L, fractionMs = 500)
        assertEquals(1_800_000_000_500L + 100, Sntp.parse(r, 48, nonce, 200))
    }

    @Test fun readsTimesPastTheYear2036() {
        val unix = 2_200_000_000L
        val ntp = unix + 2_208_988_800L - (1L shl 32)
        val r = reply(0)
        for (i in 0 until 4) r[40 + i] = (ntp shr (24 - 8 * i)).toByte()
        assertEquals(unix * 1000, Sntp.parse(r, 48, nonce, 0))
    }

    @Test fun rejectsRepliesThatAreNotForThisRequest() {
        assertNull(Sntp.parse(reply(1_800_000_000L, echo = ByteArray(8)), 48, nonce, 0))
        assertNull(Sntp.parse(reply(1_800_000_000L, mode = 3), 48, nonce, 0))
        assertNull(Sntp.parse(reply(1_800_000_000L, stratum = 0), 48, nonce, 0))
        assertNull(Sntp.parse(reply(1_800_000_000L, stratum = 16), 48, nonce, 0))
        assertNull(Sntp.parse(reply(1_800_000_000L, li = 3), 48, nonce, 0))
        assertNull(Sntp.parse(reply(1_800_000_000L), 47, nonce, 0))
        assertNull(Sntp.parse(ByteArray(48), 48, nonce, 0))
    }

    @Test fun parsesAnHttpDateHeader() {
        val ms = HttpsDate.parse("Sun, 15 Nov 2026 12:00:00 GMT", 100)
        assertEquals(1_794_744_000_000L + 500 + 50, ms)
        assertNull(HttpsDate.parse("yesterday", 0))
        assertNull(HttpsDate.parse(null, 0))
    }
}
