package com.altf4.hush.dns

/** Just enough DNS message walking to read and cap record TTLs. Never throws on bad input. */
object DnsMessage {
    private const val TYPE_OPT = 41
    private const val HEADER_LEN = 12
    private const val MAX_JUMPS = 8
    private const val DOT: Byte = 46

    /**
     * Walks every resource record in `buf[off until off + len]` and returns the smallest TTL
     * (OPT pseudo-records excluded), or -1 if the message is malformed or has no records.
     * If [cap] is not negative, each such TTL above [cap] is rewritten to [cap] in place.
     */
    fun scanTtls(buf: ByteArray, off: Int, len: Int, cap: Int): Int {
        if (len < HEADER_LEN || off < 0 || off + len > buf.size) return -1
        val end = off + len
        val questions = u16(buf, off + 4)
        val records = u16(buf, off + 6) + u16(buf, off + 8) + u16(buf, off + 10)

        var pos = off + HEADER_LEN
        for (i in 0 until questions) {
            pos = skipName(buf, pos, end)
            if (pos < 0 || pos + 4 > end) return -1
            pos += 4 // qtype + qclass
        }

        var min = Long.MAX_VALUE
        for (i in 0 until records) {
            pos = skipName(buf, pos, end)
            if (pos < 0 || pos + 10 > end) return -1
            val type = u16(buf, pos)
            val rdLength = u16(buf, pos + 8)
            val next = pos + 10 + rdLength
            if (next > end) return -1
            if (type != TYPE_OPT) {
                val ttl = u32(buf, pos + 4)
                if (ttl < min) min = ttl
                if (cap >= 0 && ttl > cap) putU32(buf, pos + 4, cap.toLong())
            }
            pos = next
        }
        return if (min == Long.MAX_VALUE) -1 else minOf(min, Int.MAX_VALUE.toLong()).toInt()
    }

    /**
     * Reads the name at [start] in the message `buf[msgOff until msgEnd]`, following compression
     * pointers, into [out] as lowercase dotted ASCII. Returns its length, or -1 if it is
     * malformed, loops, or does not fit in [out].
     */
    fun readName(buf: ByteArray, msgOff: Int, msgEnd: Int, start: Int, out: ByteArray): Int {
        var pos = start
        var n = 0
        var jumps = 0
        while (true) {
            if (pos < msgOff || pos >= msgEnd) return -1
            val b = buf[pos].toInt() and 0xFF
            if (b == 0) return n
            if ((b and 0xC0) == 0xC0) {
                if (pos + 1 >= msgEnd) return -1
                val target = msgOff + (((b and 0x3F) shl 8) or (buf[pos + 1].toInt() and 0xFF))
                jumps++
                if (jumps > MAX_JUMPS || target >= msgEnd) return -1
                pos = target
            } else if ((b and 0xC0) != 0) {
                return -1
            } else {
                if (pos + 1 + b > msgEnd) return -1
                val needed = if (n == 0) b else b + 1
                if (n + needed > out.size) return -1
                if (n != 0) out[n++] = DOT
                for (i in 0 until b) {
                    val c = buf[pos + 1 + i].toInt() and 0xFF
                    val lower = if (c >= 'A'.code && c <= 'Z'.code) c + 32 else c
                    out[n++] = lower.toByte()
                }
                pos += 1 + b
            }
        }
    }

    /** Returns the position just after the name at [start], or -1 if it runs past [end]. */
    fun skipName(buf: ByteArray, start: Int, end: Int): Int {
        var pos = start
        while (true) {
            if (pos >= end) return -1
            val b = buf[pos].toInt() and 0xFF
            if (b == 0) return pos + 1
            if ((b and 0xC0) == 0xC0) return if (pos + 2 <= end) pos + 2 else -1 // pointer ends it
            if ((b and 0xC0) != 0) return -1
            pos += 1 + b
        }
    }

    fun u16(b: ByteArray, o: Int): Int =
        ((b[o].toInt() and 0xFF) shl 8) or (b[o + 1].toInt() and 0xFF)

    private fun u32(b: ByteArray, o: Int): Long =
        ((b[o].toLong() and 0xFF) shl 24) or ((b[o + 1].toLong() and 0xFF) shl 16) or
            ((b[o + 2].toLong() and 0xFF) shl 8) or (b[o + 3].toLong() and 0xFF)

    private fun putU32(b: ByteArray, o: Int, v: Long) {
        b[o] = (v shr 24).toByte()
        b[o + 1] = (v shr 16).toByte()
        b[o + 2] = (v shr 8).toByte()
        b[o + 3] = v.toByte()
    }
}
