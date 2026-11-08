package com.altf4.hush.dns

import java.io.ByteArrayOutputStream
import java.util.Random
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsPacketTest {
    private val headers = DnsPacket.HEADERS_LEN

    /** A DNS query message (no IP/UDP headers). */
    private fun dnsQuery(
        name: String,
        id: Int = 0x1234,
        type: Int = 1,
        rd: Boolean = true,
        arcount: Int = 0,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        fun u16(v: Int) {
            out.write(v shr 8)
            out.write(v and 0xFF)
        }
        u16(id)
        u16(if (rd) 0x0100 else 0)
        u16(1)
        u16(0)
        u16(0)
        u16(arcount)
        if (name.isNotEmpty()) {
            for (label in name.split('.')) {
                out.write(label.length)
                out.write(label.toByteArray(Charsets.US_ASCII))
            }
        }
        out.write(0)
        u16(type)
        u16(1)
        return out.toByteArray()
    }

    /** Wraps a DNS payload in IPv4/UDP exactly as the tunnel would deliver it. */
    private fun packet(dns: ByteArray, srcPort: Int = 40000, dstPort: Int = 53): ByteArray {
        val pkt = ByteArray(headers + dns.size)
        System.arraycopy(dns, 0, pkt, headers, dns.size)
        DnsPacket.finishPacket(pkt, DnsPacket.CLIENT_IP, srcPort, DnsPacket.DNS_SERVER_IP, dstPort, dns.size)
        return pkt
    }

    private fun u16(b: ByteArray, o: Int) = ((b[o].toInt() and 0xFF) shl 8) or (b[o + 1].toInt() and 0xFF)

    private fun ip(b: ByteArray, o: Int) =
        ((b[o].toInt() and 0xFF) shl 24) or ((b[o + 1].toInt() and 0xFF) shl 16) or
            ((b[o + 2].toInt() and 0xFF) shl 8) or (b[o + 3].toInt() and 0xFF)

    private fun assertChecksumsValid(p: ByteArray) {
        assertEquals(0, DnsPacket.checksum(p, 0, DnsPacket.IP_HEADER_LEN))
        assertEquals(
            0,
            DnsPacket.udpChecksum(p, DnsPacket.IP_HEADER_LEN, p.size - DnsPacket.IP_HEADER_LEN, ip(p, 12), ip(p, 16)),
        )
    }

    private fun domainOf(q: DnsPacket.Query) = String(q.domain, 0, q.domainLen, Charsets.US_ASCII)

    private fun parse(pkt: ByteArray, q: DnsPacket.Query = DnsPacket.Query()) =
        DnsPacket.parseQuery(pkt, pkt.size, q)

    @Test fun parsesValidQuery() {
        val pkt = packet(dnsQuery("www.example.com", type = 28))
        val q = DnsPacket.Query()
        assertTrue(parse(pkt, q))
        assertEquals("www.example.com", domainOf(q))
        assertEquals(28, q.qtype)
        assertEquals(DnsPacket.CLIENT_IP, q.srcIp)
        assertEquals(DnsPacket.DNS_SERVER_IP, q.dstIp)
        assertEquals(40000, q.srcPort)
        assertEquals(53, q.dstPort)
        assertEquals(headers, q.dnsOffset)
        assertEquals(pkt.size - headers, q.dnsLength)
        assertEquals(pkt.size, q.questionEnd)
    }

    @Test fun lowercasesTheNameForMatching() {
        val q = DnsPacket.Query()
        assertTrue(parse(packet(dnsQuery("WwW.Example.COM")), q))
        assertEquals("www.example.com", domainOf(q))
    }

    @Test fun parsesRootQueryAsEmptyName() {
        val q = DnsPacket.Query()
        assertTrue(parse(packet(dnsQuery("")), q))
        assertEquals(0, q.domainLen)
    }

    @Test fun ignoresTrailingBytesAfterIpTotalLength() {
        val pkt = packet(dnsQuery("example.com"))
        val padded = pkt.copyOf(pkt.size + 10)
        assertTrue(DnsPacket.parseQuery(padded, padded.size, DnsPacket.Query()))
    }

    @Test fun builtPacketsHaveValidChecksums() {
        assertChecksumsValid(packet(dnsQuery("example.com")))
        assertChecksumsValid(packet(dnsQuery("a.b.c.example.org", type = 28)))
    }

    @Test fun checksumMatchesKnownVector() {
        // Well-known IPv4 header example; the correct checksum is 0xB861.
        val header = intArrayOf(
            0x45, 0x00, 0x00, 0x73, 0x00, 0x00, 0x40, 0x00, 0x40, 0x11,
            0x00, 0x00, 0xC0, 0xA8, 0x00, 0x01, 0xC0, 0xA8, 0x00, 0xC7,
        ).map { it.toByte() }.toByteArray()
        assertEquals(0xB861, DnsPacket.checksum(header, 0, header.size))
    }

    @Test fun blockedReplyIsNxdomainBuiltFromTheQuery() {
        val query = packet(dnsQuery("Ads.Example.com", id = 0xBEEF, arcount = 3))
        val q = DnsPacket.Query()
        assertTrue(parse(query, q))

        val buf = ByteArray(4160)
        val len = DnsPacket.buildBlockedPacket(query, q, buf)
        val r = buf.copyOf(len)

        // Same size as the question-only query, and valid headers.
        assertEquals(len, headers + (q.questionEnd - q.dnsOffset))
        assertChecksumsValid(r)

        // Addresses and ports are swapped.
        assertEquals(DnsPacket.DNS_SERVER_IP, ip(r, 12))
        assertEquals(DnsPacket.CLIENT_IP, ip(r, 16))
        assertEquals(53, u16(r, 20))
        assertEquals(40000, u16(r, 22))

        // DNS header: id kept, QR+RD, RA+NXDOMAIN, one question, nothing else.
        assertEquals(0xBEEF, u16(r, headers))
        assertEquals(0x8183, u16(r, headers + 2))
        assertEquals(1, u16(r, headers + 4))
        assertEquals(0, u16(r, headers + 6))
        assertEquals(0, u16(r, headers + 8))
        assertEquals(0, u16(r, headers + 10))

        // Question is copied verbatim, original case included.
        val questionLen = q.questionEnd - (q.dnsOffset + 12)
        assertArrayEquals(
            query.copyOfRange(q.dnsOffset + 12, q.dnsOffset + 12 + questionLen),
            r.copyOfRange(headers + 12, headers + 12 + questionLen),
        )

        // The reply is not itself a valid query (QR is set).
        assertFalse(DnsPacket.parseQuery(r, r.size, DnsPacket.Query()))
    }

    @Test fun blockedReplyDoesNotSetRdWhenQueryDidNot() {
        val query = packet(dnsQuery("example.com", rd = false))
        val q = DnsPacket.Query()
        assertTrue(parse(query, q))
        val buf = ByteArray(4160)
        val r = buf.copyOf(DnsPacket.buildBlockedPacket(query, q, buf))
        assertEquals(0x8083, u16(r, headers + 2))
    }

    @Test fun blockedReplyReturnsZeroWhenBufferTooSmall() {
        val query = packet(dnsQuery("example.com"))
        val q = DnsPacket.Query()
        assertTrue(parse(query, q))
        assertEquals(0, DnsPacket.buildBlockedPacket(query, q, ByteArray(20)))
    }

    @Test fun forwardedReplyWrapsAnyPayloadWithValidChecksums() {
        for (size in listOf(12, 100, 101, 1200, 4000)) {
            val payload = ByteArray(size) { (it * 7 + 3).toByte() }
            val out = ByteArray(4160)
            System.arraycopy(payload, 0, out, headers, size)
            val total = DnsPacket.finishPacket(
                out, DnsPacket.DNS_SERVER_IP, 53, DnsPacket.CLIENT_IP, 40000, size,
            )
            assertEquals(headers + size, total)
            val p = out.copyOf(total)
            assertChecksumsValid(p)
            assertEquals(total, u16(p, 2))
            assertEquals(8 + size, u16(p, 24))
            assertArrayEquals(payload, p.copyOfRange(headers, total))
        }
    }

    @Test fun truncatedInputIsRejectedAtEveryLength() {
        val pkt = packet(dnsQuery("www.example.com"))
        for (n in 0 until pkt.size) {
            assertFalse("prefix of $n bytes", DnsPacket.parseQuery(pkt.copyOf(n), n, DnsPacket.Query()))
        }
    }

    @Test fun malformedPacketsAreRejected() {
        fun mutated(change: (ByteArray) -> Unit): ByteArray {
            val p = packet(dnsQuery("a.co"))
            change(p)
            return p
        }
        assertFalse("ipv6", parse(mutated { it[0] = 0x65 }))
        assertFalse("short ihl", parse(mutated { it[0] = 0x44 }))
        assertFalse("tcp", parse(mutated { it[9] = 6 }))
        assertFalse("more fragments", parse(mutated { it[6] = 0x20 }))
        assertFalse("fragment offset", parse(mutated { it[7] = 0x01 }))
        assertFalse("ip length beyond buffer", parse(mutated { it[2] = 0x7F }))
        assertFalse("udp length beyond ip", parse(mutated { it[24] = 0x7F }))
        assertFalse("response flag", parse(mutated { it[headers + 2] = (it[headers + 2].toInt() or 0x80).toByte() }))
        assertFalse("non-standard opcode", parse(mutated { it[headers + 2] = 0x28 }))
        assertFalse("no question", parse(mutated { it[headers + 5] = 0 }))
        assertFalse("two questions", parse(mutated { it[headers + 5] = 2 }))
        assertFalse("compression pointer", parse(mutated { it[headers + 12] = 0xC0.toByte() }))
        assertFalse("label overruns packet", parse(mutated { it[headers + 12] = 63 }))
    }

    @Test fun overlongNameIsRejected() {
        val label = "a".repeat(60)
        val tooLong = listOf(label, label, label, label, label).joinToString(".") // 304 chars
        assertFalse(parse(packet(dnsQuery(tooLong))))
    }

    @Test fun randomBytesNeverThrow() {
        val rnd = Random(42)
        val q = DnsPacket.Query()
        repeat(20000) {
            val size = rnd.nextInt(220)
            val buf = ByteArray(size)
            rnd.nextBytes(buf)
            if (size > headers + 6) {
                // Steer some inputs deeper into the parser.
                buf[0] = 0x45
                buf[9] = 17
                buf[6] = 0
                buf[7] = 0
                buf[2] = (size shr 8).toByte()
                buf[3] = size.toByte()
                buf[24] = ((size - 20) shr 8).toByte()
                buf[25] = (size - 20).toByte()
                buf[headers + 2] = (buf[headers + 2].toInt() and 0x07).toByte()
                buf[headers + 4] = 0
                buf[headers + 5] = 1
            }
            DnsPacket.parseQuery(buf, size, q) // must not throw
        }
    }

    @Test fun corruptedValidPacketsNeverThrow() {
        val rnd = Random(7)
        val base = packet(dnsQuery("www.example.com"))
        val q = DnsPacket.Query()
        repeat(20000) {
            val p = base.copyOf()
            repeat(1 + rnd.nextInt(4)) { p[rnd.nextInt(p.size)] = rnd.nextInt(256).toByte() }
            val len = if (rnd.nextBoolean()) p.size else rnd.nextInt(p.size + 1)
            DnsPacket.parseQuery(p, len, q) // must not throw
        }
    }
}
