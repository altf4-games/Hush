package com.altf4.hush.dns

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlocklistTest {
    private fun Blocklist.blocks(name: String): Boolean {
        val bytes = name.toByteArray(Charsets.US_ASCII)
        return matches(bytes, bytes.size)
    }

    private val list = Blocklist.build(listOf("ads.example.com", "tracker.net"))

    @Test fun matchesExactAndSubdomains() {
        assertTrue(list.blocks("ads.example.com"))
        assertTrue(list.blocks("x.ads.example.com"))
        assertTrue(list.blocks("x.y.z.ads.example.com"))
        assertTrue(list.blocks("tracker.net"))
        assertTrue(list.blocks("cdn.tracker.net"))
    }

    @Test fun doesNotMatchParentsOrSiblings() {
        assertFalse(list.blocks("example.com"))
        assertFalse(list.blocks("other.example.com"))
        assertFalse(list.blocks("com"))
        assertFalse(list.blocks("net"))
    }

    @Test fun matchesOnLabelBoundariesOnly() {
        assertFalse(list.blocks("nottracker.net"))
        assertFalse(list.blocks("xads.example.com"))
        assertFalse(list.blocks("tracker.net.evil.org"))
    }

    @Test fun emptyAndSingleLabelNamesAreNeverBlocked() {
        assertFalse(list.blocks(""))
        assertFalse(list.blocks("localhost"))
        assertFalse(Blocklist.EMPTY.blocks("ads.example.com"))
    }

    @Test fun bareTldEntriesAreIgnored() {
        val tldOnly = Blocklist.build(listOf("com", "net"))
        assertEquals(0, tldOnly.size)
        assertFalse(tldOnly.blocks("example.com"))
    }

    @Test fun buildDeduplicatesNormalisesAndSkipsInvalid() {
        val l = Blocklist.build(listOf("a.com", "A.com", "a.com", "not a domain", "", "-bad.com"))
        assertEquals(1, l.size)
        assertTrue(l.blocks("a.com"))
    }

    @Test fun twoLabelEntryBlocksOnlyThatDomainAndItsSubdomains() {
        val l = Blocklist.build(listOf("t.co"))
        assertTrue(l.blocks("t.co"))
        assertTrue(l.blocks("a.t.co"))
        assertFalse(l.blocks("microsoft.co"))
        assertFalse(l.blocks("at.co"))
    }

    @Test fun textAndByteHashesAgree() {
        val name = "ads.example.com"
        val bytes = name.toByteArray(Charsets.US_ASCII)
        assertEquals(Blocklist.hash(name), Blocklist.hash(bytes, 0, bytes.size))
    }

    @Test fun largeListsStayCorrect() {
        val domains = (0 until 50_000).map { "host$it.example.org" }
        val l = Blocklist.build(domains)
        assertEquals(50_000, l.size)
        assertTrue(l.blocks("host0.example.org"))
        assertTrue(l.blocks("www.host49999.example.org"))
        assertFalse(l.blocks("host50000.example.org"))
        assertFalse(l.blocks("example.org"))
    }
}
