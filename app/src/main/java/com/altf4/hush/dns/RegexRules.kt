package com.altf4.hush.dns

import java.util.regex.Pattern

/** A set of compiled, vetted domain patterns (see [SafeRegex]). Immutable. */
class RegexRules private constructor(private val patterns: Array<Pattern>) {
    val size: Int get() = patterns.size

    fun isEmpty(): Boolean = patterns.isEmpty()

    /** True if any pattern matches somewhere in [domain] (lowercase, dotted). */
    fun matches(domain: CharSequence): Boolean {
        for (p in patterns) {
            if (SafeRegex.matches(p, domain)) return true
        }
        return false
    }

    companion object {
        val EMPTY = RegexRules(emptyArray())

        /** Compiles the valid patterns in [sources], at most [SafeRegex.MAX_RULES]; others are skipped. */
        fun of(sources: Collection<String>): RegexRules {
            val compiled = sources.sorted()
                .take(SafeRegex.MAX_RULES)
                .mapNotNull { SafeRegex.compile(it) }
            return if (compiled.isEmpty()) EMPTY else RegexRules(compiled.toTypedArray())
        }
    }
}
