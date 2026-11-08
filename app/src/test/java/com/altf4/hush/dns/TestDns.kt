package com.altf4.hush.dns

import java.io.ByteArrayOutputStream

/** Builders for DNS messages used by several tests. */
object TestDns {
    private fun ByteArrayOutputStream.u16(v: Int) {
        write((v shr 8) and 0xFF)
        write(v and 0xFF)
    }

    private fun ByteArrayOutputStream.u32(v: Long) {
        write(((v shr 24) and 0xFF).toInt())
        write(((v shr 16) and 0xFF).toInt())
        write(((v shr 8) and 0xFF).toInt())
        write((v and 0xFF).toInt())
    }

    private fun ByteArrayOutputStream.name(name: String) {
        if (name.isNotEmpty()) {
            for (label in name.split('.')) {
                write(label.length)
                write(label.toByteArray(Charsets.US_ASCII))
            }
        }
        write(0)
    }

    fun encodedNameLength(name: String): Int = if (name.isEmpty()) 1 else name.length + 2

    /** A standard recursive query for [name]. */
    fun query(name: String, id: Int = 0x1234, type: Int = 1): ByteArray {
        val out = ByteArrayOutputStream()
        out.u16(id)
        out.u16(0x0100)
        out.u16(1)
        out.u16(0)
        out.u16(0)
        out.u16(0)
        out.name(name)
        out.u16(type)
        out.u16(1)
        return out.toByteArray()
    }

    /**
     * A response to [query] with one answer per entry of [answerTtls] (A records), one authority
     * record per entry of [authorityTtls], and optionally an EDNS OPT record whose TTL field holds
     * flags. Every record's name is a compression pointer to the question.
     */
    fun response(
        name: String,
        id: Int = 0x1234,
        type: Int = 1,
        rcode: Int = 0,
        truncated: Boolean = false,
        answerTtls: List<Int> = listOf(300),
        authorityTtls: List<Int> = emptyList(),
        withOpt: Boolean = false,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        out.u16(id)
        out.u16(0x8180 or (if (truncated) 0x0200 else 0) or rcode)
        out.u16(1)
        out.u16(answerTtls.size)
        out.u16(authorityTtls.size)
        out.u16(if (withOpt) 1 else 0)
        out.name(name)
        out.u16(type)
        out.u16(1)
        for (ttl in answerTtls) {
            out.u16(0xC00C)
            out.u16(1)
            out.u16(1)
            out.u32(ttl.toLong())
            out.u16(4)
            out.write(byteArrayOf(1, 2, 3, 4))
        }
        for (ttl in authorityTtls) {
            out.u16(0xC00C)
            out.u16(2)
            out.u16(1)
            out.u32(ttl.toLong())
            out.u16(0)
        }
        if (withOpt) {
            out.write(0)
            out.u16(41)
            out.u16(4096)
            out.u32(0x8000L)
            out.u16(0)
        }
        return out.toByteArray()
    }

    /** TTLs of the answer records of a message built by [response], read from `msg[off..]`. */
    fun answerTtls(msg: ByteArray, off: Int, name: String): List<Long> {
        val count = ((msg[off + 6].toInt() and 0xFF) shl 8) or (msg[off + 7].toInt() and 0xFF)
        val start = off + 12 + encodedNameLength(name) + 4
        return (0 until count).map { i ->
            val p = start + i * 16 + 6 // pointer(2) + type(2) + class(2) precede the TTL
            ((msg[p].toLong() and 0xFF) shl 24) or ((msg[p + 1].toLong() and 0xFF) shl 16) or
                ((msg[p + 2].toLong() and 0xFF) shl 8) or (msg[p + 3].toLong() and 0xFF)
        }
    }
}
