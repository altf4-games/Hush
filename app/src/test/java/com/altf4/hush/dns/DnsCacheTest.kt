package com.altf4.hush.dns

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsCacheTest {
    private var now = 0L
    private val cache = DnsCache(maxEntries = 4, clock = { now })
    private val name = "example.com"
    private val offset = 28

    private fun sec(s: Long) = s * 1_000_000_000L

    private fun keyFor(query: ByteArray) = cache.key(query, 0, query.size)

    private fun fetch(key: Long, out: ByteArray = ByteArray(4160)): Pair<Int, ByteArray> =
        cache.get(key, 0x56, 0x78, out, offset) to out

    @Test fun missThenHitWithTheCallersTransactionId() {
        val key = keyFor(TestDns.query(name))
        assertEquals(0, fetch(key).first)

        val response = TestDns.response(name, id = 0x1111)
        cache.put(key, response, 0, response.size)
        val (n, out) = fetch(key)

        assertEquals(response.size, n)
        assertEquals(0x56, out[offset].toInt())
        assertEquals(0x78, out[offset + 1].toInt())
        assertEquals(
            response.copyOfRange(2, response.size).toList(),
            out.copyOfRange(offset + 2, offset + n).toList(),
        )
    }

    @Test fun keyIgnoresTheTransactionIdButNotTheQuestion() {
        val a = keyFor(TestDns.query(name, id = 1))
        assertEquals(a, keyFor(TestDns.query(name, id = 2)))
        assertNotEquals(a, keyFor(TestDns.query("example.org")))
        assertNotEquals(a, keyFor(TestDns.query(name, type = 28)))
    }

    @Test fun ttlsAreCappedToTheTimeLeft() {
        val key = keyFor(TestDns.query(name))
        val response = TestDns.response(name, answerTtls = listOf(300))
        cache.put(key, response, 0, response.size)

        now = sec(100)
        val (n, out) = fetch(key)
        assertTrue(n > 0)
        assertEquals(listOf(200L), TestDns.answerTtls(out, offset, name))
    }

    @Test fun shortTtlsLiveAtLeastThirtySeconds() {
        val key = keyFor(TestDns.query(name))
        val response = TestDns.response(name, answerTtls = listOf(5))
        cache.put(key, response, 0, response.size)

        now = sec(10)
        val (n, out) = fetch(key)
        assertTrue(n > 0)
        assertEquals(listOf(5L), TestDns.answerTtls(out, offset, name)) // never raised above the original

        now = sec(29)
        assertTrue(fetch(key).first > 0)
        now = sec(31)
        assertEquals(0, fetch(key).first)
    }

    @Test fun longTtlsAreLimitedToTenMinutes() {
        val key = keyFor(TestDns.query(name))
        val response = TestDns.response(name, answerTtls = listOf(100_000))
        cache.put(key, response, 0, response.size)

        val (n, out) = fetch(key)
        assertTrue(n > 0)
        assertEquals(listOf(600L), TestDns.answerTtls(out, offset, name))

        now = sec(599)
        assertTrue(fetch(key).first > 0)
        now = sec(601)
        assertEquals(0, fetch(key).first)
    }

    @Test fun nxdomainIsCachedUsingTheAuthorityTtl() {
        val key = keyFor(TestDns.query(name))
        val response = TestDns.response(name, rcode = 3, answerTtls = emptyList(), authorityTtls = listOf(120))
        cache.put(key, response, 0, response.size)
        assertEquals(response.size, fetch(key).first)
    }

    @Test fun unsuitableRepliesAreNotCached() {
        val key = keyFor(TestDns.query(name))
        val notCacheable = listOf(
            TestDns.response(name, truncated = true),
            TestDns.response(name, rcode = 2), // SERVFAIL
            TestDns.response(name, rcode = 5), // REFUSED
            TestDns.response(name, answerTtls = emptyList()), // no records to take a TTL from
            TestDns.query(name), // not a response at all
        )
        for (msg in notCacheable) {
            cache.put(key, msg, 0, msg.size)
            assertEquals(0, fetch(key).first)
        }
    }

    @Test fun leastRecentlyUsedEntryIsEvicted() {
        val small = DnsCache(maxEntries = 2, clock = { now })
        val response = TestDns.response(name)
        small.put(1L, response, 0, response.size)
        small.put(2L, response, 0, response.size)
        val out = ByteArray(4160)
        assertTrue(small.get(1L, 0, 0, out, offset) > 0) // touch 1 so 2 is the eldest
        small.put(3L, response, 0, response.size)

        assertEquals(0, small.get(2L, 0, 0, out, offset))
        assertTrue(small.get(1L, 0, 0, out, offset) > 0)
        assertTrue(small.get(3L, 0, 0, out, offset) > 0)
    }

    @Test fun tooSmallOutputBufferIsAMiss() {
        val key = keyFor(TestDns.query(name))
        val response = TestDns.response(name)
        cache.put(key, response, 0, response.size)
        assertEquals(0, cache.get(key, 0, 0, ByteArray(response.size + offset - 1), offset))
    }

    @Test fun clearEmptiesTheCache() {
        val key = keyFor(TestDns.query(name))
        val response = TestDns.response(name)
        cache.put(key, response, 0, response.size)
        cache.clear()
        assertEquals(0, fetch(key).first)
    }

    @Test fun repeatedHitsDoNotAlterTheStoredReply() {
        val key = keyFor(TestDns.query(name))
        val response = TestDns.response(name, answerTtls = listOf(300))
        cache.put(key, response, 0, response.size)

        now = sec(250)
        assertEquals(listOf(50L), TestDns.answerTtls(fetch(key).second, offset, name))
        now = sec(251)
        assertEquals(listOf(49L), TestDns.answerTtls(fetch(key).second, offset, name))
    }
}
