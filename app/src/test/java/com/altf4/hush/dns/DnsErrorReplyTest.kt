package com.altf4.hush.dns

import org.junit.Assert.assertEquals
import org.junit.Test

class DnsErrorReplyTest {
    private fun u16(b: ByteArray, o: Int) = ((b[o].toInt() and 0xFF) shl 8) or (b[o + 1].toInt() and 0xFF)

    @Test fun servfailReplyKeepsIdAndQuestion() {
        val query = TestDns.query("example.com", id = 0xBEEF)
        val out = ByteArray(200)
        val n = DnsPacket.writeErrorReply(query, 0, query.size, out, 28, DnsPacket.RCODE_SERVFAIL)

        assertEquals(query.size, n)
        assertEquals(0xBEEF, u16(out, 28))
        assertEquals(0x8182, u16(out, 30)) // QR, RD (copied from the query), RA, RCODE=2
        assertEquals(1, u16(out, 32))
        assertEquals(0, u16(out, 34))
        assertEquals(0, u16(out, 36))
        assertEquals(0, u16(out, 38))
        assertEquals(
            query.copyOfRange(12, query.size).toList(),
            out.copyOfRange(28 + 12, 28 + n).toList(),
        )
    }

    @Test fun nxdomainUsesResponseCodeThree() {
        val query = TestDns.query("example.com")
        val out = ByteArray(200)
        DnsPacket.writeErrorReply(query, 0, query.size, out, 0, DnsPacket.RCODE_NXDOMAIN)
        assertEquals(0x8183, u16(out, 2))
    }

    @Test fun tooSmallBufferOrTooShortQueryGivesZero() {
        val query = TestDns.query("example.com")
        assertEquals(0, DnsPacket.writeErrorReply(query, 0, query.size, ByteArray(10), 0, 3))
        assertEquals(0, DnsPacket.writeErrorReply(query, 0, 11, ByteArray(200), 0, 3))
    }
}
