package com.altf4.hush.dns

import java.io.ByteArrayOutputStream
import java.util.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResponseGuardTest {
    private val scratch = ByteArray(256)

    private fun filter(
        blocked: List<String> = listOf("tracker.net"),
        allowed: List<String> = emptyList(),
        uncloak: Boolean = true,
        rebinding: Boolean = true,
    ) = DomainFilter(
        allow = Blocklist.build(allowed),
        block = listOf(Blocklist.build(blocked)),
        uncloakCname = uncloak,
        blockRebinding = rebinding,
    )

    private fun ByteArrayOutputStream.u16(v: Int) {
        write((v shr 8) and 0xFF)
        write(v and 0xFF)
    }

    private fun ByteArrayOutputStream.name(name: String) {
        for (label in name.split('.')) {
            write(label.length)
            write(label.toByteArray(Charsets.US_ASCII))
        }
        write(0)
    }

    /** A record whose owner name points back at the question. */
    private fun record(type: Int, rdata: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.u16(0xC00C)
        out.u16(type)
        out.u16(1)
        out.u16(0)
        out.u16(60)
        out.u16(rdata.size)
        out.write(rdata)
        return out.toByteArray()
    }

    private fun a(vararg octets: Int) = record(1, ByteArray(4) { octets[it].toByte() })

    private fun aaaa(bytes: ByteArray) = record(28, bytes)

    private fun cname(target: String): ByteArray {
        val rdata = ByteArrayOutputStream().also { it.name(target) }.toByteArray()
        return record(5, rdata)
    }

    /** CNAME whose target is a compression pointer to the question name. */
    private fun cnameToQuestion() = record(5, byteArrayOf(0xC0.toByte(), 0x0C))

    private fun reply(question: String, vararg records: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.u16(0x1234)
        out.u16(0x8180)
        out.u16(1)
        out.u16(records.size)
        out.u16(0)
        out.u16(0)
        out.name(question)
        out.u16(1)
        out.u16(1)
        for (r in records) out.write(r)
        return out.toByteArray()
    }

    private fun blocks(msg: ByteArray, f: DomainFilter = filter()) =
        ResponseGuard.shouldBlock(msg, 0, msg.size, f, scratch)

    private fun v6(vararg head: Int, tail: Int = 0): ByteArray {
        val b = ByteArray(16)
        head.forEachIndexed { i, v -> b[i] = v.toByte() }
        b[15] = tail.toByte()
        return b
    }

    // ---- CNAME uncloaking ----

    @Test fun aliasOfABlockedTrackerIsBlocked() {
        val msg = reply("www.example.com", cname("metrics.tracker.net"), a(93, 184, 216, 34))
        assertTrue(blocks(msg))
    }

    @Test fun aliasOfAnOrdinaryNameIsFine() {
        val msg = reply("www.example.com", cname("cdn.example.net"), a(93, 184, 216, 34))
        assertFalse(blocks(msg))
    }

    @Test fun compressedAliasTargetsAreDecoded() {
        // The alias target is a pointer to the question "ads.tracker.net" itself.
        val msg = reply("ads.tracker.net", cnameToQuestion(), a(93, 184, 216, 34))
        assertTrue(blocks(msg))
    }

    @Test fun anAllowedTargetIsNotTreatedAsATracker() {
        val msg = reply("www.example.com", cname("metrics.tracker.net"), a(93, 184, 216, 34))
        assertFalse(blocks(msg, filter(allowed = listOf("metrics.tracker.net"))))
    }

    @Test fun uncloakingCanBeSwitchedOff() {
        val msg = reply("www.example.com", cname("metrics.tracker.net"), a(93, 184, 216, 34))
        assertFalse(blocks(msg, filter(uncloak = false)))
    }

    // ---- Rebinding ----

    @Test fun privateIpv4AnswersAreBlocked() {
        for (ip in listOf(
            intArrayOf(10, 0, 0, 5), intArrayOf(192, 168, 1, 2), intArrayOf(127, 0, 0, 1),
            intArrayOf(169, 254, 1, 1), intArrayOf(172, 16, 0, 1), intArrayOf(172, 31, 255, 255),
            intArrayOf(100, 64, 0, 1), intArrayOf(0, 0, 0, 0),
        )) {
            val msg = reply("www.example.com", a(*ip))
            assertTrue(ip.joinToString("."), blocks(msg))
        }
    }

    @Test fun publicIpv4AnswersAreFine() {
        for (ip in listOf(
            intArrayOf(8, 8, 8, 8), intArrayOf(172, 32, 0, 1), intArrayOf(172, 15, 0, 1),
            intArrayOf(100, 63, 0, 1), intArrayOf(100, 128, 0, 1), intArrayOf(192, 169, 0, 1),
            intArrayOf(93, 184, 216, 34),
        )) {
            val msg = reply("www.example.com", a(*ip))
            assertFalse(ip.joinToString("."), blocks(msg))
        }
    }

    @Test fun privateIpv6AnswersAreBlocked() {
        assertTrue(blocks(reply("www.example.com", aaaa(v6(tail = 1))))) // ::1
        assertTrue(blocks(reply("www.example.com", aaaa(v6(tail = 0))))) // ::
        assertTrue(blocks(reply("www.example.com", aaaa(v6(0xFE, 0x80, tail = 1))))) // fe80::
        assertTrue(blocks(reply("www.example.com", aaaa(v6(0xFD, 0x00, tail = 1))))) // fd00::
        assertTrue(blocks(reply("www.example.com", aaaa(v6(0xFC, 0x00, tail = 1))))) // fc00::
        val mappedPrivate = v6().also {
            it[10] = 0xFF.toByte(); it[11] = 0xFF.toByte()
            it[12] = 10; it[15] = 1
        }
        assertTrue(blocks(reply("www.example.com", aaaa(mappedPrivate)))) // ::ffff:10.0.0.1
    }

    @Test fun publicIpv6AnswersAreFine() {
        val google = v6(0x26, 0x06, 0x47, 0x00, tail = 0x11) // 2606:4700::11
        assertFalse(blocks(reply("www.example.com", aaaa(google))))
        val mappedPublic = v6().also {
            it[10] = 0xFF.toByte(); it[11] = 0xFF.toByte()
            it[12] = 8; it[13] = 8; it[14] = 8; it[15] = 8
        }
        assertFalse(blocks(reply("www.example.com", aaaa(mappedPublic)))) // ::ffff:8.8.8.8
        assertFalse(blocks(reply("www.example.com", aaaa(v6(0xFE, 0xC0, tail = 1))))) // outside fe80::/10
        assertFalse(blocks(reply("www.example.com", aaaa(v6(tail = 2))))) // ::2 is not special here
    }

    @Test fun rebindingProtectionCanBeSwitchedOff() {
        val msg = reply("www.example.com", a(192, 168, 1, 2))
        assertFalse(blocks(msg, filter(rebinding = false)))
    }

    @Test fun bothProtectionsOffMeansNothingIsInspected() {
        val msg = reply("www.example.com", cname("x.tracker.net"), a(10, 0, 0, 1))
        assertFalse(blocks(msg, filter(uncloak = false, rebinding = false)))
    }

    // ---- Robustness ----

    @Test fun truncatedRepliesNeverThrowAndAreNotBlocked() {
        val msg = reply("www.example.com", cname("metrics.tracker.net"), a(10, 0, 0, 1))
        for (n in 0 until msg.size) {
            // A cut-off message is passed through; it must simply not crash.
            ResponseGuard.shouldBlock(msg.copyOf(n), 0, n, filter(), scratch)
        }
    }

    @Test fun badBoundsAreIgnored() {
        val msg = reply("www.example.com", a(10, 0, 0, 1))
        assertFalse(ResponseGuard.shouldBlock(msg, 0, msg.size + 1, filter(), scratch))
        assertFalse(ResponseGuard.shouldBlock(msg, -1, msg.size, filter(), scratch))
    }

    @Test fun corruptedRepliesNeverThrow() {
        val rnd = Random(5)
        val base = reply("www.example.com", cname("metrics.tracker.net"), a(1, 2, 3, 4), aaaa(v6(tail = 1)))
        repeat(20000) {
            val m = base.copyOf()
            repeat(1 + rnd.nextInt(5)) { m[rnd.nextInt(m.size)] = rnd.nextInt(256).toByte() }
            val len = if (rnd.nextBoolean()) m.size else rnd.nextInt(m.size + 1)
            ResponseGuard.shouldBlock(m, 0, len, filter(), scratch)
        }
    }

    @Test fun privateRangeHelpersAgreeWithTheirBoundaries() {
        assertEquals(true, ResponseGuard.isPrivateV4(byteArrayOf(10, 1, 2, 3), 0))
        assertEquals(false, ResponseGuard.isPrivateV4(byteArrayOf(11, 1, 2, 3), 0))
        assertEquals(true, ResponseGuard.isPrivateV4(byteArrayOf(172.toByte(), 31, 0, 0), 0))
        assertEquals(false, ResponseGuard.isPrivateV4(byteArrayOf(172.toByte(), 32, 0, 0), 0))
    }
}
