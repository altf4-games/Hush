package com.altf4.hush.dns

import java.util.Locale

/**
 * Turns one line of a downloaded list into a domain, or null if the line is not a plain domain
 * rule. Lenient on junk: anything unexpected is skipped, never thrown.
 */
object ListParser {
    private val HOSTS_IPS = setOf("0.0.0.0", "127.0.0.1")

    private val LOCAL_NAMES = setOf(
        "localhost", "localhost.localdomain", "local", "broadcasthost",
        "ip6-localhost", "ip6-loopback", "ip6-localnet", "ip6-mcastprefix",
        "ip6-allnodes", "ip6-allrouters", "ip6-allhosts",
    )

    fun parseLine(line: String, format: ListFormat): String? = when (format) {
        ListFormat.Hosts -> parseHosts(line)
        ListFormat.Adblock -> parseAdblock(line)
    }

    /** `0.0.0.0 domain [# comment]`. Only the first host on a line is used. */
    private fun parseHosts(raw: String): String? {
        val hash = raw.indexOf('#')
        val line = (if (hash >= 0) raw.substring(0, hash) else raw).trim()
        if (line.isEmpty()) return null
        val parts = line.split(' ', '\t').filter { it.isNotEmpty() }
        if (parts.size < 2 || parts[0] !in HOSTS_IPS) return null
        return normalizeDomain(parts[1])
    }

    /** Exactly `||domain^`. Rules with paths, wildcards, options or exceptions are skipped. */
    private fun parseAdblock(raw: String): String? {
        val line = raw.trim()
        if (!line.startsWith("||") || !line.endsWith("^")) return null
        return normalizeDomain(line.substring(2, line.length - 1))
    }

    /**
     * Returns the lowercase domain if [candidate] is a valid multi-label hostname, else null.
     * Rejects single labels (bare TLDs), IP literals and local-only names.
     */
    fun normalizeDomain(candidate: String): String? {
        val domain = candidate.trim().lowercase(Locale.ROOT)
        if (domain.isEmpty() || domain.length > 253 || domain in LOCAL_NAMES) return null
        val labels = domain.split('.')
        if (labels.size < 2) return null
        for (label in labels) {
            if (label.isEmpty() || label.length > 63) return null
            if (label.first() == '-' || label.last() == '-') return null
            for (c in label) {
                val ok = c in 'a'..'z' || c in '0'..'9' || c == '-' || c == '_'
                if (!ok) return null
            }
        }
        // An all-digit last label means an IP address such as 1.2.3.4, not a domain.
        if (labels.last().all { it in '0'..'9' }) return null
        return domain
    }
}
