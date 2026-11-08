package com.altf4.hush.dns

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainFilterRegexTest {
    private val filter = DomainFilter(
        allow = Blocklist.build(listOf("allowed.example")),
        block = listOf(Blocklist.build(listOf("blocked.example"))),
        allowRegex = RegexRules.of(listOf("^safe\\.")),
        blockRegex = RegexRules.of(listOf("^ads[0-9]*\\.", "tracker")),
    )

    private fun hashBlocked(name: String): Boolean {
        val b = name.toByteArray(Charsets.US_ASCII)
        return filter.isBlocked(b, b.size)
    }

    private fun regexBlocked(name: String): Boolean {
        val b = name.toByteArray(Charsets.US_ASCII)
        return filter.isRegexBlocked(b, b.size)
    }

    @Test fun listedNamesAreBlockedWithoutLookingAtPatterns() {
        assertTrue(hashBlocked("x.blocked.example"))
        assertFalse(hashBlocked("fine.org"))
    }

    @Test fun anAllowPatternOverridesAListedName() {
        assertTrue(hashBlocked("blocked.example"))
        assertFalse(hashBlocked("safe.blocked.example"))
    }

    @Test fun blockPatternsCatchUnlistedNames() {
        assertTrue(regexBlocked("ads1.site.org"))
        assertTrue(regexBlocked("my.tracker.net"))
        assertFalse(regexBlocked("fine.org"))
        assertFalse(regexBlocked("badsite.org")) // "^ads" is anchored
    }

    @Test fun allowedSitesAndAllowPatternsOverrideBlockPatterns() {
        assertFalse(regexBlocked("ads1.allowed.example"))
        assertFalse(regexBlocked("safe.ads.site.org"))
    }

    @Test fun noBlockPatternsMeansNoPatternChecking() {
        val plain = DomainFilter(Blocklist.EMPTY, emptyList())
        val b = "ads1.site.org".toByteArray(Charsets.US_ASCII)
        assertFalse(plain.isRegexBlocked(b, b.size))
    }

    @Test fun isAllowedReflectsTheAllowListOnly() {
        val allowed = "x.allowed.example".toByteArray(Charsets.US_ASCII)
        val other = "safe.site.org".toByteArray(Charsets.US_ASCII)
        assertTrue(filter.isAllowed(allowed, allowed.size))
        assertFalse(filter.isAllowed(other, other.size))
    }
}
