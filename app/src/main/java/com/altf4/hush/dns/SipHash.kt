package com.altf4.hush.dns

/**
 * SipHash-2-4, a keyed 64-bit hash. Used where hashes of names that outside parties can choose
 * (the DNS cache key) must not be predictable: without the secret key nobody can construct two
 * names that collide. Not thread-safe; callers serialise access.
 */
class SipHash24(private val k0: Long, private val k1: Long) {
    private var v0 = 0L
    private var v1 = 0L
    private var v2 = 0L
    private var v3 = 0L

    /** Hash of `data[start until end]`. */
    fun hash(data: ByteArray, start: Int, end: Int): Long {
        v0 = k0 xor 0x736f6d6570736575L
        v1 = k1 xor 0x646f72616e646f6dL
        v2 = k0 xor 0x6c7967656e657261L
        v3 = k1 xor 0x7465646279746573L

        val length = end - start
        val blocksEnd = start + (length and 7.inv())
        var i = start
        while (i < blocksEnd) {
            var m = 0L
            for (k in 7 downTo 0) m = (m shl 8) or (data[i + k].toLong() and 0xFF)
            absorb(m)
            i += 8
        }

        var last = length.toLong() shl 56
        var shift = 0
        while (i < end) {
            last = last or ((data[i].toLong() and 0xFF) shl shift)
            shift += 8
            i++
        }
        absorb(last)

        v2 = v2 xor 0xFF
        round(); round(); round(); round()
        return v0 xor v1 xor v2 xor v3
    }

    private fun absorb(m: Long) {
        v3 = v3 xor m
        round(); round()
        v0 = v0 xor m
    }

    private fun round() {
        v0 += v1; v1 = java.lang.Long.rotateLeft(v1, 13); v1 = v1 xor v0; v0 = java.lang.Long.rotateLeft(v0, 32)
        v2 += v3; v3 = java.lang.Long.rotateLeft(v3, 16); v3 = v3 xor v2
        v0 += v3; v3 = java.lang.Long.rotateLeft(v3, 21); v3 = v3 xor v0
        v2 += v1; v1 = java.lang.Long.rotateLeft(v1, 17); v1 = v1 xor v2; v2 = java.lang.Long.rotateLeft(v2, 32)
    }
}
