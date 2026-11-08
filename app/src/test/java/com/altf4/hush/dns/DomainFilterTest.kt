package com.altf4.hush.dns

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainFilterTest {
    private fun DomainFilter.blocks(name: String): Boolean {
        val bytes = name.toByteArray(Charsets.US_ASCII)
        return isBlocked(bytes, bytes.size)
    }

    private val rules = Blocklist.build(listOf("social.example", "ads.example.com"))
    private val downloaded = Blocklist.build(listOf("tracker.net", "cdn.example.com"))

    @Test fun anyBlockListCanBlock() {
        val filter = DomainFilter(Blocklist.EMPTY, listOf(rules, downloaded))
        assertTrue(filter.blocks("ads.example.com"))
        assertTrue(filter.blocks("a.tracker.net"))
        assertFalse(filter.blocks("example.com"))
        assertFalse(filter.blocks("fine.org"))
    }

    @Test fun allowedSitesWinOverEveryList() {
        val filter = DomainFilter(Blocklist.build(listOf("tracker.net")), listOf(rules, downloaded))
        assertFalse(filter.blocks("tracker.net"))
        assertFalse(filter.blocks("cdn.tracker.net")) // subdomains of an allowed site are allowed
        assertTrue(filter.blocks("ads.example.com")) // other rules still apply
    }

    @Test fun allowingAParentAllowsItsBlockedChildren() {
        val filter = DomainFilter(Blocklist.build(listOf("example.com")), listOf(rules, downloaded))
        assertFalse(filter.blocks("ads.example.com"))
        assertFalse(filter.blocks("cdn.example.com"))
    }

    @Test fun emptyFilterBlocksNothing() {
        assertFalse(DomainFilter.EMPTY.blocks("ads.example.com"))
    }
}
