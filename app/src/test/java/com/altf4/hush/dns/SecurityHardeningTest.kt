package com.altf4.hush.dns

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Checks for the fixes that came out of the security review. */
class SecurityHardeningTest {
    private fun bytes(name: String) = name.toByteArray(Charsets.ISO_8859_1)

    // The reference vectors from the SipHash paper: key 00 01 .. 0f, input 00 01 .. (n - 1).
    private val reference = SipHash24(0x0706050403020100L, 0x0f0e0d0c0b0a0908L)

    @Test fun sipHashMatchesTheReferenceVectors() {
        assertEquals(0x726fdb47dd0e0e31L, reference.hash(ByteArray(0), 0, 0))
        val input = ByteArray(15) { it.toByte() }
        assertEquals(-0x5ed6359eb641ba1bL, reference.hash(input, 0, 15)) // 0xa129ca6149be45e5
    }

    @Test fun sipHashDependsOnTheKeyAndTheRange() {
        val data = bytes("example.com")
        val other = SipHash24(1L, 2L)
        assertNotEquals(reference.hash(data, 0, data.size), other.hash(data, 0, data.size))
        assertEquals(reference.hash(data, 3, 9), reference.hash(data.copyOfRange(3, 9), 0, 6))
    }

    @Test fun cacheKeysCannotBeComputedWithoutTheProcessKey() {
        val query = TestDns.query("example.com")
        val a = DnsCache(seed0 = 11L, seed1 = 22L).key(query, 0, query.size)
        val b = DnsCache(seed0 = 33L, seed1 = 44L).key(query, 0, query.size)
        assertNotEquals(a, b)
        // And it is no longer the unkeyed list hash that anyone can compute offline.
        assertNotEquals(Blocklist.hash(query, 2, query.size), a)
        // The same cache is still stable for the same question, whatever the transaction id.
        val cache = DnsCache(seed0 = 11L, seed1 = 22L)
        assertEquals(
            cache.key(TestDns.query("example.com", id = 1), 0, query.size),
            cache.key(TestDns.query("example.com", id = 2), 0, query.size),
        )
    }

    /** `evil.test` stands for a name crafted to share the hash of an allowed name. */
    @Test fun allowListHashHitMustBeAnExactName() {
        val allowHashes = Blocklist.fromSorted(
            longArrayOf(Blocklist.hash("good.example"), Blocklist.hash("evil.test")).sortedArray(),
        )
        val blocked = Blocklist.build(listOf("evil.test", "tracker.example"))
        val strict = DomainFilter(allowHashes, listOf(blocked), allowNames = setOf("good.example"))

        fun isAllowed(name: String) = strict.isAllowed(bytes(name), name.length)
        fun isBlocked(name: String) = strict.isBlocked(bytes(name), name.length)

        assertTrue(isAllowed("good.example"))
        assertTrue(isAllowed("www.good.example")) // a subdomain of an allowed name
        assertFalse(isAllowed("evil.test")) // same hash, not the name that was allowed
        assertTrue(isBlocked("evil.test")) // so it is still blocked
        assertFalse(isAllowed("other.example"))
    }

    @Test fun withoutNamesTheAllowListWorksAsBefore() {
        val allowHashes = Blocklist.build(listOf("good.example"))
        val filter = DomainFilter(allowHashes, listOf(Blocklist.build(listOf("good.example"))))
        assertTrue(filter.isAllowed(bytes("good.example"), "good.example".length))
        assertFalse(filter.isBlocked(bytes("good.example"), "good.example".length))
    }
}
