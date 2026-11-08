package com.altf4.hush.dns

/**
 * Everything the DNS service decides about a name. Allowed sites always win.
 *
 * Order of checks:
 *  1. [isBlocked] (every query, cheap): allow list, then hashed lists. A hashed-list hit can still
 *     be overridden by an allow pattern.
 *  2. [isRegexBlocked] (only for names that passed 1 and missed the cache): allow list, allow
 *     patterns, then block patterns.
 *  3. [ResponseGuard] on the upstream reply, unless the name is allowed ([isAllowed]).
 *
 * @param uncloakCname block names whose CNAME chain reaches a blocked domain
 * @param blockRebinding block public names that answer with private addresses
 */
class DomainFilter(
    private val allow: Blocklist,
    private val block: List<Blocklist>,
    private val allowRegex: RegexRules = RegexRules.EMPTY,
    private val blockRegex: RegexRules = RegexRules.EMPTY,
    val uncloakCname: Boolean = true,
    val blockRebinding: Boolean = true,
) {
    fun isAllowed(domain: ByteArray, len: Int): Boolean = allow.matches(domain, len)

    fun isBlocked(domain: ByteArray, len: Int): Boolean {
        if (allow.matches(domain, len)) return false
        for (list in block) {
            if (list.matches(domain, len)) {
                return allowRegex.isEmpty() || !allowRegex.matches(text(domain, len))
            }
        }
        return false
    }

    fun isRegexBlocked(domain: ByteArray, len: Int): Boolean {
        if (blockRegex.isEmpty()) return false
        if (allow.matches(domain, len)) return false
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
