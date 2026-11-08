package com.altf4.hush.dns

import com.altf4.hush.data.NightClock

/**
 * Everything the DNS service decides about a name. Allowed sites always win.
 *
 * Order of checks:
 *  1. [isBlocked] (every query, cheap): allow list, then the quiet-hours list (only while the
 *     window is active), then the hashed lists. A hit can still be overridden by an allow pattern.
 *  2. [isRegexBlocked] (only for names that passed 1 and missed the cache): allow list, allow
 *     patterns, then block patterns.
 *  3. [ResponseGuard] on the upstream reply, unless the name is allowed ([isAllowed]).
 *
 * @param allowNames the allowed domains themselves. The allow list is matched by hash, and anyone
 *   can choose the names they look up, so a name built to collide with an allowed one could skip
 *   blocking and the rebinding check; when this is given, a hash hit must also be an exact name.
 * @param uncloakCname block names whose CNAME chain reaches a blocked domain
 * @param blockRebinding block public names that answer with private addresses
 * @param nightBlock names blocked only while [nightClock] says the quiet window is active
 */
class DomainFilter(
    private val allow: Blocklist,
    private val block: List<Blocklist>,
    private val allowRegex: RegexRules = RegexRules.EMPTY,
    private val blockRegex: RegexRules = RegexRules.EMPTY,
    val uncloakCname: Boolean = true,
    val blockRebinding: Boolean = true,
    private val nightBlock: Blocklist = Blocklist.EMPTY,
    private val nightClock: NightClock? = null,
    allowNames: Set<String>? = null,
) {
    private val allowCheck: Blocklist.SuffixCheck? = allowNames?.let { names ->
        Blocklist.SuffixCheck { d, start, end -> names.contains(String(d, start, end - start, Charsets.ISO_8859_1)) }
    }

    fun isAllowed(domain: ByteArray, len: Int): Boolean = allow.matches(domain, len, allowCheck)

    fun isBlocked(domain: ByteArray, len: Int): Boolean {
        if (isAllowed(domain, len)) return false
        var hit = nightClock != null && nightBlock.size > 0 &&
            nightClock.isActive() && nightBlock.matches(domain, len)
        if (!hit) {
            for (list in block) {
                if (list.matches(domain, len)) {
                    hit = true
                    break
                }
            }
        }
        if (!hit) return false
        return allowRegex.isEmpty() || !allowRegex.matches(text(domain, len))
    }

    fun isRegexBlocked(domain: ByteArray, len: Int): Boolean {
        if (blockRegex.isEmpty()) return false
        if (isAllowed(domain, len)) return false
        val name = text(domain, len)
        if (allowRegex.matches(name)) return false
        return blockRegex.matches(name)
    }

    private fun text(domain: ByteArray, len: Int): String =
        String(domain, 0, len, Charsets.ISO_8859_1)

    companion object {
        val EMPTY = DomainFilter(Blocklist.EMPTY, emptyList())
    }
}
