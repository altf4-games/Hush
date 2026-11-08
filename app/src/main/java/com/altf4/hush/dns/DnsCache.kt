package com.altf4.hush.dns

import java.security.SecureRandom

/**
 * Small LRU of upstream replies. Keys are a keyed 64-bit hash of the whole query (everything after the
 * transaction id), so no domain text is kept and queries that differ in flags or EDNS options
 * never share an entry. Lifetime follows the records' TTL clamped to 30 s – 10 min.
 *
 * Only successful answers and NXDOMAIN are stored; truncated replies, SERVFAIL and the like are
 * not. On a hit the transaction id is replaced and record TTLs are capped to the time left.
 */
class DnsCache(
    private val maxEntries: Int = 512,
    private val clock: () -> Long = System::nanoTime,
    seed0: Long = SecureRandom().nextLong(),
    seed1: Long = SecureRandom().nextLong(),
) {
    private class Entry(val data: ByteArray, val expiresAtNanos: Long)

    private val lock = Any()

    // Any app can send lookups for names it chooses, so the key must not be predictable: with an
    // unkeyed hash an app could craft a name whose answer it controls that shares a cache slot with
    // another name, and have the other name resolve to its address. The key is random per process.
    private val hasher = SipHash24(seed0, seed1)
    private val hasherLock = Any()

    private val map = object : LinkedHashMap<Long, Entry>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Entry>?): Boolean =
            size > maxEntries
    }

    /** Cache key for the query `buf[off until off + len]`, ignoring its transaction id. */
    fun key(buf: ByteArray, off: Int, len: Int): Long =
        synchronized(hasherLock) { hasher.hash(buf, off + 2, off + len) }

    /** Stores a reply if it is cacheable. [response] holds the DNS message at `off until off + len`. */
    fun put(key: Long, response: ByteArray, off: Int, len: Int) {
        if (len < 12 || off + len > response.size) return
        val flags = DnsMessage.u16(response, off + 2)
        if ((flags and 0x8000) == 0) return // not a response
        if ((flags and 0x0200) != 0) return // truncated
        val rcode = flags and 0x0F
        if (rcode != 0 && rcode != 3) return // only NOERROR and NXDOMAIN

        val minTtl = DnsMessage.scanTtls(response, off, len, -1)
        if (minTtl < 0) return
        val ttlSeconds = minTtl.coerceIn(MIN_TTL_SECONDS, MAX_TTL_SECONDS)

        val entry = Entry(
            response.copyOfRange(off, off + len),
            clock() + ttlSeconds * NANOS_PER_SECOND,
        )
        synchronized(lock) { map[key] = entry }
    }

    /**
     * Copies a fresh cached reply into `out[outOffset..]` with [id0]/[id1] as its transaction id
     * and returns its length, or 0 on a miss, an expired entry, or if [out] is too small.
     */
    fun get(key: Long, id0: Byte, id1: Byte, out: ByteArray, outOffset: Int): Int {
        val now = clock()
        val entry = synchronized(lock) {
            val e = map[key]
            if (e != null && e.expiresAtNanos - now <= 0) {
                map.remove(key)
                null
            } else {
                e
            }
        } ?: return 0

        val size = entry.data.size
        if (outOffset + size > out.size) return 0
        System.arraycopy(entry.data, 0, out, outOffset, size)
        out[outOffset] = id0
        out[outOffset + 1] = id1

        val remainingSeconds = ((entry.expiresAtNanos - now) / NANOS_PER_SECOND).toInt()
        DnsMessage.scanTtls(out, outOffset, size, maxOf(remainingSeconds, 1))
        return size
    }

    fun clear() {
        synchronized(lock) { map.clear() }
    }

    companion object {
        const val MIN_TTL_SECONDS = 30
        const val MAX_TTL_SECONDS = 600
        private const val NANOS_PER_SECOND = 1_000_000_000L
    }
}
