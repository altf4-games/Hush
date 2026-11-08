package com.altf4.hush.dns

/**
 * IPv4 + UDP + DNS parsing and building for the tunnel. Pure functions over byte arrays; no
 * allocation on the parse path. Anything malformed makes [parseQuery] return false and is dropped.
 *
 * Addresses are big-endian Ints (10.111.222.2 -> 0x0A6FDE02).
 */
object DnsPacket {
    /** The fake DNS server the system is told to use; the only address routed into the tunnel. */
    const val DNS_SERVER_IP = 0x0A6FDE02

    /** The tunnel's own address (source of nothing we send; used by tests and for clarity). */
    const val CLIENT_IP = 0x0A6FDE01

    const val DNS_PORT = 53
    const val IP_HEADER_LEN = 20
    const val UDP_HEADER_LEN = 8

    /** IPv4 (no options) + UDP. The DNS payload of a packet we build starts here. */
    const val HEADERS_LEN = IP_HEADER_LEN + UDP_HEADER_LEN

    const val MAX_NAME_LEN = 253

    private const val PROTO_UDP = 17
    private const val DNS_HEADER_LEN = 12

    /** Reusable parse result. Create one per reader thread. */
    class Query {
        var srcIp = 0
        var dstIp = 0
        var srcPort = 0
        var dstPort = 0

        /** Offset and length of the DNS message inside the packet. */
        var dnsOffset = 0
        var dnsLength = 0

        /** Offset just past the (single) question inside the packet. */
        var questionEnd = 0
        var qtype = 0

        /** Lowercase dotted name without a trailing dot, e.g. `www.example.com`. */
        val domain = ByteArray(MAX_NAME_LEN)
        var domainLen = 0
    }

    /**
     * Parses [buf] (first [len] bytes) as an IPv4/UDP packet carrying a standard DNS query with
     * exactly one uncompressed question. Returns false for anything else, including truncated or
     * malformed input. Never throws for any byte content.
     */
    fun parseQuery(buf: ByteArray, len: Int, q: Query): Boolean {
        if (len > buf.size || len < HEADERS_LEN + DNS_HEADER_LEN) return false

        val b0 = buf[0].toInt() and 0xFF
        if ((b0 shr 4) != 4) return false
        val ihl = (b0 and 0x0F) * 4
        if (ihl < IP_HEADER_LEN) return false

        val total = u16(buf, 2)
        if (total > len || total < ihl + UDP_HEADER_LEN + DNS_HEADER_LEN) return false
        if ((buf[9].toInt() and 0xFF) != PROTO_UDP) return false
        // More-fragments flag or a non-zero fragment offset: we do not reassemble.
        if ((u16(buf, 6) and 0x3FFF) != 0) return false

        val udpLen = u16(buf, ihl + 4)
        if (udpLen < UDP_HEADER_LEN + DNS_HEADER_LEN || ihl + udpLen > total) return false

        val dns = ihl + UDP_HEADER_LEN
        val end = ihl + udpLen

        val flags = u16(buf, dns + 2)
        if ((flags and 0x8000) != 0) return false // already a response
        if (((flags shr 11) and 0x0F) != 0) return false // not a standard query
        if (u16(buf, dns + 4) != 1) return false // exactly one question

        // Question name: uncompressed labels, copied lowercase into q.domain.
        var pos = dns + DNS_HEADER_LEN
        var n = 0
        while (true) {
            if (pos >= end) return false
            val labelLen = buf[pos].toInt() and 0xFF
            if (labelLen == 0) {
                pos++
                break
            }
            if ((labelLen and 0xC0) != 0) return false // compression pointer or reserved bits
            if (pos + 1 + labelLen > end) return false
            val needed = if (n == 0) labelLen else labelLen + 1
            if (n + needed > MAX_NAME_LEN) return false
            if (n != 0) q.domain[n++] = DOT
            for (i in 0 until labelLen) {
                val c = buf[pos + 1 + i].toInt() and 0xFF
                val lower = if (c >= 'A'.code && c <= 'Z'.code) c + 32 else c
                q.domain[n++] = lower.toByte()
            }
            pos += 1 + labelLen
        }
        if (pos + 4 > end) return false // qtype + qclass

        q.srcIp = ip(buf, 12)
        q.dstIp = ip(buf, 16)
        q.srcPort = u16(buf, ihl)
        q.dstPort = u16(buf, ihl + 2)
        q.dnsOffset = dns
        q.dnsLength = udpLen - UDP_HEADER_LEN
        q.qtype = u16(buf, pos)
        q.questionEnd = pos + 4
        q.domainLen = n
        return true
    }

    /**
     * Builds a complete NXDOMAIN reply packet for [q] (parsed from [pkt]) into [out].
     * QR and RA set, RCODE=3, RD copied, all counts zero except one question, truncated after the
     * question. Returns the packet length, or 0 if [out] is too small.
     */
    fun buildBlockedPacket(pkt: ByteArray, q: Query, out: ByteArray): Int {
        val dnsLen = writeErrorReply(pkt, q.dnsOffset, q.questionEnd, out, HEADERS_LEN, RCODE_NXDOMAIN)
        if (dnsLen == 0) return 0
        return finishPacket(out, q.dstIp, q.dstPort, q.srcIp, q.srcPort, dnsLen)
    }

    const val RCODE_SERVFAIL = 2
    const val RCODE_NXDOMAIN = 3

    /**
     * Writes an error reply with response code [rcode] (NXDOMAIN to block, SERVFAIL when the
     * upstream is unreachable) to the query at `src[srcOff until questionEnd]` (header plus its
     * single question) into `out` at [outOff]. Returns the message length, or 0 if [out] is too
     * small.
     */
    fun writeErrorReply(
        src: ByteArray,
        srcOff: Int,
        questionEnd: Int,
        out: ByteArray,
        outOff: Int,
        rcode: Int,
    ): Int {
        val dnsLen = questionEnd - srcOff
        if (dnsLen < DNS_HEADER_LEN || outOff + dnsLen > out.size) return 0
        out[outOff] = src[srcOff]
        out[outOff + 1] = src[srcOff + 1]
        val rd = src[srcOff + 2].toInt() and 0x01
        out[outOff + 2] = (0x80 or rd).toByte() // QR=1, opcode 0, AA=0, TC=0, RD copied
        out[outOff + 3] = (0x80 or (rcode and 0x0F)).toByte() // RA=1, then the response code
        out[outOff + 4] = 0
        out[outOff + 5] = 1 // QDCOUNT = 1
        for (i in 6 until DNS_HEADER_LEN) out[outOff + i] = 0
        System.arraycopy(
            src, srcOff + DNS_HEADER_LEN,
            out, outOff + DNS_HEADER_LEN,
            dnsLen - DNS_HEADER_LEN,
        )
        return dnsLen
    }

    /**
     * Writes the IPv4 and UDP headers (with checksums) around a DNS payload already placed at
     * `out[HEADERS_LEN until HEADERS_LEN + dnsLen]`. Returns the total packet length.
     */
    fun finishPacket(out: ByteArray, srcIp: Int, srcPort: Int, dstIp: Int, dstPort: Int, dnsLen: Int): Int {
        val total = HEADERS_LEN + dnsLen
        out[0] = 0x45 // IPv4, 5 words
        out[1] = 0
        putU16(out, 2, total)
        putU16(out, 4, 0) // identification
        putU16(out, 6, 0x4000) // don't fragment
        out[8] = 64 // TTL
        out[9] = PROTO_UDP.toByte()
        putU16(out, 10, 0)
        putIp(out, 12, srcIp)
        putIp(out, 16, dstIp)
        putU16(out, 10, checksum(out, 0, IP_HEADER_LEN))

        val udpLen = UDP_HEADER_LEN + dnsLen
        putU16(out, 20, srcPort)
        putU16(out, 22, dstPort)
        putU16(out, 24, udpLen)
        putU16(out, 26, 0)
        val cs = udpChecksum(out, IP_HEADER_LEN, udpLen, srcIp, dstIp)
        putU16(out, 26, if (cs == 0) 0xFFFF else cs)
        return total
    }

    /** Internet checksum over [len] bytes. A header that includes a valid checksum yields 0. */
    fun checksum(buf: ByteArray, off: Int, len: Int): Int = fold(sum(buf, off, len, 0L))

    /** UDP checksum including the IPv4 pseudo-header. A segment with a valid checksum yields 0. */
    fun udpChecksum(buf: ByteArray, udpOff: Int, udpLen: Int, srcIp: Int, dstIp: Int): Int {
        var s = (srcIp ushr 16).toLong() + (srcIp and 0xFFFF) +
            (dstIp ushr 16) + (dstIp and 0xFFFF) + PROTO_UDP + udpLen
        s = sum(buf, udpOff, udpLen, s)
        return fold(s)
    }

    private const val DOT: Byte = 46

    private fun sum(buf: ByteArray, off: Int, len: Int, initial: Long): Long {
        var s = initial
        var i = off
        val end = off + len
        while (i + 1 < end) {
            s += ((buf[i].toInt() and 0xFF) shl 8) or (buf[i + 1].toInt() and 0xFF)
            i += 2
        }
        if (i < end) s += (buf[i].toInt() and 0xFF) shl 8
        return s
    }

    private fun fold(sum: Long): Int {
        var x = sum
        while ((x shr 16) != 0L) x = (x and 0xFFFF) + (x shr 16)
        return (x.inv() and 0xFFFF).toInt()
    }

    private fun u16(b: ByteArray, o: Int): Int =
        ((b[o].toInt() and 0xFF) shl 8) or (b[o + 1].toInt() and 0xFF)

    private fun ip(b: ByteArray, o: Int): Int =
        ((b[o].toInt() and 0xFF) shl 24) or
            ((b[o + 1].toInt() and 0xFF) shl 16) or
            ((b[o + 2].toInt() and 0xFF) shl 8) or
            (b[o + 3].toInt() and 0xFF)

    private fun putU16(b: ByteArray, o: Int, v: Int) {
        b[o] = (v shr 8).toByte()
        b[o + 1] = v.toByte()
    }

    private fun putIp(b: ByteArray, o: Int, v: Int) {
        b[o] = (v ushr 24).toByte()
        b[o + 1] = (v ushr 16).toByte()
        b[o + 2] = (v ushr 8).toByte()
        b[o + 3] = v.toByte()
    }
}
