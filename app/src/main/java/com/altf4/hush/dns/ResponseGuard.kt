package com.altf4.hush.dns

/**
 * Inspects an upstream reply before it is cached or handed to an app, and says whether it should
 * be replaced by NXDOMAIN:
 *
 *  - **CNAME uncloaking:** a name that is an alias for a blocked tracker domain is blocked too,
 *    which defeats trackers disguised as first-party subdomains.
 *  - **DNS rebinding protection:** a public name answering with a private, loopback or
 *    link-local address is blocked, so a website cannot point its own name at the local network.
 *
 * Only the answer section is read. Malformed replies are passed through untouched (the encrypted
 * upstream already authenticates their source). Never throws.
 */
object ResponseGuard {
    private const val TYPE_A = 1
    private const val TYPE_CNAME = 5
    private const val TYPE_AAAA = 28
    private const val HEADER_LEN = 12

    /**
     * @param scratch reusable buffer of at least 255 bytes for decoding names
     */
    fun shouldBlock(
        buf: ByteArray,
        off: Int,
        len: Int,
        filter: DomainFilter,
        scratch: ByteArray,
    ): Boolean {
        if (!filter.uncloakCname && !filter.blockRebinding) return false
        if (len < HEADER_LEN || off < 0 || off + len > buf.size) return false
        val end = off + len

        val questions = DnsMessage.u16(buf, off + 4)
        val answers = DnsMessage.u16(buf, off + 6)

        var pos = off + HEADER_LEN
        for (i in 0 until questions) {
            pos = DnsMessage.skipName(buf, pos, end)
            if (pos < 0 || pos + 4 > end) return false
            pos += 4
        }

        for (i in 0 until answers) {
            pos = DnsMessage.skipName(buf, pos, end)
            if (pos < 0 || pos + 10 > end) return false
            val type = DnsMessage.u16(buf, pos)
            val rdLength = DnsMessage.u16(buf, pos + 8)
            val data = pos + 10
            val next = data + rdLength
            if (next > end) return false

            when (type) {
                TYPE_CNAME -> if (filter.uncloakCname) {
                    val n = DnsMessage.readName(buf, off, end, data, scratch)
                    if (n > 0 && filter.isBlocked(scratch, n)) return true
                }
                TYPE_A -> if (filter.blockRebinding && rdLength == 4 && isPrivateV4(buf, data)) {
                    return true
                }
                TYPE_AAAA -> if (filter.blockRebinding && rdLength == 16 && isPrivateV6(buf, data)) {
                    return true
                }
            }
            pos = next
        }
        return false
    }

    /** 0/8, 10/8, 100.64/10, 127/8, 169.254/16, 172.16/12, 192.168/16. */
    fun isPrivateV4(b: ByteArray, o: Int): Boolean {
        val a = b[o].toInt() and 0xFF
        val c = b[o + 1].toInt() and 0xFF
        return a == 0 ||
            a == 10 ||
            a == 127 ||
            (a == 100 && c in 64..127) ||
            (a == 169 && c == 254) ||
            (a == 172 && c in 16..31) ||
            (a == 192 && c == 168)
    }

    /** ::, ::1, fc00::/7, fe80::/10, and IPv4-mapped addresses of a private IPv4 address. */
    fun isPrivateV6(b: ByteArray, o: Int): Boolean {
        val first = b[o].toInt() and 0xFF
        val second = b[o + 1].toInt() and 0xFF
        if ((first and 0xFE) == 0xFC) return true // unique local
        if (first == 0xFE && (second and 0xC0) == 0x80) return true // link-local

        var leadingZeros = true
        for (i in 0 until 10) {
            if (b[o + i].toInt() != 0) {
                leadingZeros = false
                break
            }
        }
        if (!leadingZeros) return false

        val lastTwo = ((b[o + 10].toInt() and 0xFF) shl 8) or (b[o + 11].toInt() and 0xFF)
        if (lastTwo == 0xFFFF) return isPrivateV4(b, o + 12) // ::ffff:a.b.c.d
        if (lastTwo != 0) return false
        // ::/96 -- unspecified (::) and loopback (::1) have the first 15 bytes zero.
        for (i in 12 until 15) {
            if (b[o + i].toInt() != 0) return false
        }
        return (b[o + 15].toInt() and 0xFF) <= 1
    }
}
