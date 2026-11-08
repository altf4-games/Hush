package com.altf4.hush.dns

import java.util.Arrays

/**
 * Immutable set of blocked domains stored as a sorted array of 64-bit hashes. Lookups walk the
 * name's labels from the left and binary-search each suffix, so blocking `example.com` also
 * blocks `ads.example.com`. A bare TLD is never matched. No per-query allocation, and no domain
 * strings are kept: only hashes.
 */
class Blocklist private constructor(private val hashes: LongArray) {
    val size: Int get() = hashes.size

    /** [domain] is lowercase dotted ASCII, first [len] bytes, no trailing dot. */
    fun isBlocked(domain: ByteArray, len: Int): Boolean {
        if (hashes.isEmpty() || len <= 0) return false

        var lastDot = -1
        for (i in len - 1 downTo 0) {
            if (domain[i] == DOT) {
                lastDot = i
                break
            }
        }
        if (lastDot < 0) return false // single label: a bare TLD or hostname

        // Candidate suffixes begin at each label start up to (and including) the last dot's label,
        // so every candidate has at least two labels.
        var start = 0
        while (start <= lastDot) {
            if (Arrays.binarySearch(hashes, hash(domain, start, len)) >= 0) return true
            var i = start
            while (domain[i] != DOT) i++
            start = i + 1
        }
        return false
    }

    companion object {
        val EMPTY = Blocklist(LongArray(0))

        private const val DOT: Byte = 46
        private val FNV_OFFSET = 0xcbf29ce484222325uL.toLong()
        private const val FNV_PRIME = 0x100000001b3L

        /** FNV-1a 64-bit over `domain[start until end]`. */
        fun hash(domain: ByteArray, start: Int, end: Int): Long {
            var h = FNV_OFFSET
            for (i in start until end) {
                h = (h xor (domain[i].toLong() and 0xFF)) * FNV_PRIME
            }
            return h
        }

        /** Same hash for a domain given as text (ASCII). */
        fun hash(domain: String): Long {
            var h = FNV_OFFSET
            for (c in domain) {
                h = (h xor (c.code.toLong() and 0xFF)) * FNV_PRIME
            }
            return h
        }

        /** Builds a list from domain names. Invalid entries (and bare TLDs) are skipped. */
        fun build(domains: Collection<String>): Blocklist {
            val tmp = LongArray(domains.size)
            var n = 0
            for (d in domains) {
                val normalized = ListParser.normalizeDomain(d) ?: continue
                tmp[n++] = hash(normalized)
            }
            if (n == 0) return EMPTY
            Arrays.sort(tmp, 0, n)
            var m = 1
            for (i in 1 until n) {
                if (tmp[i] != tmp[m - 1]) tmp[m++] = tmp[i]
            }
            return Blocklist(tmp.copyOf(m))
        }
    }
}
