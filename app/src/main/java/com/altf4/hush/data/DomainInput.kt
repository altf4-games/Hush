package com.altf4.hush.data

import com.altf4.hush.dns.ListParser
import java.util.Locale

/** Turns what a person types or pastes (a domain or a full URL) into a clean domain. */
object DomainInput {
    /**
     * `https://www.Example.com:8080/a?b#c` becomes `example.com`. A leading `www.` is dropped so
     * the whole site is covered. Returns null if the result is not a valid multi-label domain.
     */
    fun parse(input: String): String? {
        var s = input.trim().lowercase(Locale.ROOT)
        if (s.isEmpty()) return null

        val scheme = s.indexOf("://")
        if (scheme >= 0) s = s.substring(scheme + 3)

        val stop = s.indexOfFirst { it == '/' || it == '?' || it == '#' }
        if (stop >= 0) s = s.substring(0, stop)

        val at = s.lastIndexOf('@')
        if (at >= 0) s = s.substring(at + 1)

        val colon = s.indexOf(':')
        if (colon >= 0) s = s.substring(0, colon)

        s = s.trimEnd('.')
        if (s.startsWith("www.") && s.count { it == '.' } >= 2) s = s.substring(4)

        return ListParser.normalizeDomain(s)
    }
}
