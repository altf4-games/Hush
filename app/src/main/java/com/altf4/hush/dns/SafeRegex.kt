package com.altf4.hush.dns

import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

/**
 * Regular expressions for domain rules, made safe to run on every lookup. A backtracking engine
 * can be made to run for minutes by a crafted pattern (ReDoS); that would stall DNS for the whole
 * device. Three layers keep that from happening:
 *
 *  1. Patterns are screened when saved: length cap, no back-references, no obviously nested
 *     repetition such as `(a+)+`.
 *  2. Each pattern is test-run against worst-case-looking names under a step budget; one that
 *     blows the budget is refused.
 *  3. Every real match runs under the same step budget. If it is exceeded the match is abandoned
 *     and counts as "no match", so a bad pattern can never hang the resolver.
 *
 * Patterns use `find()` semantics (a match anywhere in the name), are case-insensitive, and apply
 * to the lowercase dotted name. Anchor with `^` and `$` for whole-name matches.
 */
object SafeRegex {
    const val MAX_PATTERN_LENGTH = 200
    const val MAX_RULES = 100

    /** Roughly 0.3 ms of work at worst; real patterns use a few hundred steps. */
    private const val STEP_BUDGET = 50_000

    private val BACKREFERENCE = Regex("""\\[1-9]|\\k<""")
    private val NESTED_REPEAT = Regex("""\([^)]*[*+}][^)]*\)\s*[*+{]""")

    private val PROBES = listOf(
        "a".repeat(63) + ".example.com",
        "a-".repeat(30) + "b.example.com",
        "a.".repeat(50) + "com",
        "a".repeat(40) + "!",
        "ab".repeat(30) + "!",
        "x".repeat(60) + "!",
        "0123456789".repeat(10) + ".net",
    )

    private class BudgetExceeded : RuntimeException(null, null, false, false)

    /** A CharSequence that stops the regex engine after too many character reads. */
    private class Budgeted(private val text: CharSequence, private var steps: Int) : CharSequence {
        override val length: Int get() = text.length

        override fun get(index: Int): Char {
            if (--steps < 0) throw BudgetExceeded()
            return text[index]
        }

        override fun subSequence(startIndex: Int, endIndex: Int): CharSequence =
            Budgeted(text.subSequence(startIndex, endIndex), steps)

        override fun toString(): String = text.toString()
    }

    /** Returns null if [pattern] is acceptable, otherwise a short reason for the person using it. */
    fun check(pattern: String): String? {
        if (pattern.isBlank()) return "Enter a pattern"
        if (pattern.length > MAX_PATTERN_LENGTH) return "Too long (max $MAX_PATTERN_LENGTH characters)"
        if (BACKREFERENCE.containsMatchIn(pattern)) return "Back-references are not allowed"
        if (NESTED_REPEAT.containsMatchIn(pattern)) {
            return "Nested repetition like (a+)+ is not allowed; it can be very slow"
        }
        val compiled = try {
            Pattern.compile(pattern, Pattern.CASE_INSENSITIVE)
        } catch (e: PatternSyntaxException) {
            return "Not a valid pattern"
        }
        try {
            for (probe in PROBES) execute(compiled, probe)
        } catch (e: BudgetExceeded) {
            return "Too slow; simplify the pattern"
        }
        return null
    }

    /** Compiles [pattern] if it passes [check], otherwise null. */
    fun compile(pattern: String): Pattern? =
        if (check(pattern) == null) Pattern.compile(pattern, Pattern.CASE_INSENSITIVE) else null

    /** True if [pattern] matches somewhere in [text]. A match that runs out of budget is false. */
    fun matches(pattern: Pattern, text: CharSequence): Boolean =
        try {
            execute(pattern, text)
        } catch (e: BudgetExceeded) {
            false
        }

    private fun execute(pattern: Pattern, text: CharSequence): Boolean =
        pattern.matcher(Budgeted(text, STEP_BUDGET)).find()
}
