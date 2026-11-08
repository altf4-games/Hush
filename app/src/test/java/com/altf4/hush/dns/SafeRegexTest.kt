package com.altf4.hush.dns

import java.util.regex.Pattern
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeRegexTest {
    @Test fun ordinaryPatternsAreAccepted() {
        val good = listOf(
            "^ads[0-9]*\\.",
            "tracker",
            "\\.example\\.(com|net)$",
            "(ads|track)\\d+",
            "^[a-z]{3}-metrics\\.",
            ".*ads.*",
            "^(www\\.)?example\\.com$",
        )
        for (p in good) assertNull(p, SafeRegex.check(p))
    }

    @Test fun blankAndBrokenPatternsAreRejected() {
        assertNotNull(SafeRegex.check(""))
        assertNotNull(SafeRegex.check("   "))
        assertNotNull(SafeRegex.check("("))
        assertNotNull(SafeRegex.check("["))
        assertNotNull(SafeRegex.check("*abc"))
    }

    @Test fun overlongPatternsAreRejected() {
        assertNull(SafeRegex.check("a".repeat(SafeRegex.MAX_PATTERN_LENGTH)))
        assertNotNull(SafeRegex.check("a".repeat(SafeRegex.MAX_PATTERN_LENGTH + 1)))
    }

    @Test fun backReferencesAreRejected() {
        assertNotNull(SafeRegex.check("(a)\\1"))
        assertNotNull(SafeRegex.check("(?<x>a)\\k<x>"))
    }

    @Test fun nestedRepetitionIsRejected() {
        for (p in listOf("(a+)+", "(.*)*", "(a*)+", "(x+x+)+y", "^(\\w+\\s?)*$", "(a{2,})+")) {
            assertNotNull(p, SafeRegex.check(p))
        }
    }

    @Test fun patternsThatBacktrackBadlyAreRejectedByTheTestRun() {
        // Not caught by the nested-repetition screen, but polynomial-time blow-up on any engine
        // when the name has no "!" (newer JVMs optimise classic (a|aa)+ cases, so avoid those).
        assertNotNull(SafeRegex.check(".*.*.*.*.*!"))
        assertNotNull(SafeRegex.check(".*a.*a.*a.*a.*!"))
    }

    @Test fun compileOnlyReturnsVettedPatterns() {
        assertNotNull(SafeRegex.compile("^ads\\."))
        assertNull(SafeRegex.compile("("))
        assertNull(SafeRegex.compile("(a+)+"))
    }

    @Test fun matchingIsCaseInsensitiveAndFindsAnywhere() {
        val p = SafeRegex.compile("ads\\.")!!
        assertTrue(SafeRegex.matches(p, "x.ads.example.com"))
        assertTrue(SafeRegex.matches(p, "X.ADS.EXAMPLE.COM"))
        assertFalse(SafeRegex.matches(p, "example.com"))
    }

    @Test fun anchorsMatchWholeNames() {
        val p = SafeRegex.compile("^example\\.com$")!!
        assertTrue(SafeRegex.matches(p, "example.com"))
        assertFalse(SafeRegex.matches(p, "www.example.com"))
    }

    @Test fun aRunawayMatchIsAbandonedQuicklyAndCountsAsNoMatch() {
        // Bypass check() on purpose: this is a pattern that must never be allowed to hang us.
        val evil = Pattern.compile("^(a|aa)+$", Pattern.CASE_INSENSITIVE)
        val input = "a".repeat(40) + "!"
        val start = System.nanoTime()
        val result = SafeRegex.matches(evil, input)
        val elapsedMs = (System.nanoTime() - start) / 1_000_000
        assertFalse(result)
        assertTrue("took ${elapsedMs}ms", elapsedMs < 2000)
    }

    @Test fun theBudgetDoesNotAffectNormalMatching() {
        val p = SafeRegex.compile("^ads[0-9]*\\.")!!
        assertTrue(SafeRegex.matches(p, "ads12.example.com"))
        assertEquals(false, SafeRegex.matches(p, "bads12.example.com"))
    }

    @Test fun longNamesWithSimplePatternsStayWithinBudget() {
        val p = SafeRegex.compile("tracker")!!
        val name = ("a".repeat(60) + ".").repeat(4) + "tracker.com" // about 250 characters
        assertTrue(SafeRegex.matches(p, name))
    }
}
