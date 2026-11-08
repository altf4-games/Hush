package com.altf4.hush.dns

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RegexRulesTest {
    @Test fun invalidAndUnsafePatternsAreSkipped() {
        val rules = RegexRules.of(listOf("^ads\\.", "(", "(a+)+", "track"))
        assertEquals(2, rules.size)
        assertTrue(rules.matches("ads.example.com"))
        assertTrue(rules.matches("my.tracker.org"))
        assertFalse(rules.matches("example.com"))
    }

    @Test fun emptyInputGivesTheEmptySet() {
        val rules = RegexRules.of(emptyList())
        assertTrue(rules.isEmpty())
        assertFalse(rules.matches("anything.com"))
        assertTrue(RegexRules.EMPTY.isEmpty())
    }

    @Test fun ruleCountIsCapped() {
        val rules = RegexRules.of((0 until 250).map { "p$it" })
        assertEquals(SafeRegex.MAX_RULES, rules.size)
    }

    @Test fun anyOneMatchingPatternIsEnough() {
        val rules = RegexRules.of(listOf("^one\\.", "^two\\."))
        assertTrue(rules.matches("two.example.com"))
        assertFalse(rules.matches("three.example.com"))
    }
}
