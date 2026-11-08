package com.altf4.hush.dns

import java.io.BufferedReader
import java.io.StringReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ListCollectTest {
    private fun collect(text: String, format: ListFormat): Pair<Int, LongArray> {
        val sink = HashBuffer(16)
        val accepted = ListParser.collect(BufferedReader(StringReader(text)), format, sink)
        return accepted to sink.sortedUnique()
    }

    @Test fun hostsListKeepsOnlyValidDomains() {
        val text = """
            # header
            127.0.0.1 localhost
            0.0.0.0 ads.example.com
            0.0.0.0 tracker.net # trailing
            0.0.0.0 ads.example.com

            not a hosts line
        """.trimIndent()
        val (accepted, hashes) = collect(text, ListFormat.Hosts)
        assertEquals(3, accepted) // the duplicate counts here...
        assertEquals(2, hashes.size) // ...and is removed when merged
    }

    @Test fun adblockListKeepsOnlyPlainDomainRules() {
        val text = """
            [Adblock Plus]
            ! Title: test
            ||ads.example.com^
            ||tracker.net^${'$'}third-party
            @@||allowed.example.com^
            ||cdn.example.org^
        """.trimIndent()
        val (accepted, hashes) = collect(text, ListFormat.Adblock)
        assertEquals(2, accepted)
        assertEquals(2, hashes.size)
    }

    @Test fun collectedHashesMatchTheListLookup() {
        val (_, hashes) = collect("0.0.0.0 ads.example.com", ListFormat.Hosts)
        val list = Blocklist.fromSorted(hashes)
        val name = "x.ads.example.com".toByteArray(Charsets.US_ASCII)
        assertTrue(list.matches(name, name.size))
    }

    @Test fun emptyInputAcceptsNothing() {
        assertEquals(0, collect("", ListFormat.Hosts).first)
        assertEquals(0, collect("", ListFormat.Adblock).first)
    }
}
