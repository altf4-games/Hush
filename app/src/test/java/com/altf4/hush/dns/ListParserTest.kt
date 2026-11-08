package com.altf4.hush.dns

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ListParserTest {
    private fun hosts(line: String) = ListParser.parseLine(line, ListFormat.Hosts)
    private fun adblock(line: String) = ListParser.parseLine(line, ListFormat.Adblock)

    @Test fun hostsBasicLine() {
        assertEquals("ads.example.com", hosts("0.0.0.0 ads.example.com"))
        assertEquals("ads.example.com", hosts("127.0.0.1\tads.example.com"))
    }

    @Test fun hostsLowercasesAndStripsTrailingComment() {
        assertEquals("ads.example.com", hosts("0.0.0.0 Ads.Example.COM # tracker"))
    }

    @Test fun hostsSkipsCommentsBlankAndLocalNames() {
        assertNull(hosts(""))
        assertNull(hosts("   "))
        assertNull(hosts("# a comment"))
        assertNull(hosts("127.0.0.1 localhost"))
        assertNull(hosts("0.0.0.0 broadcasthost"))
        assertNull(hosts("::1 localhost"))
        assertNull(hosts("0.0.0.0 0.0.0.0"))
    }

    @Test fun hostsRejectsBareTldIpAndMissingHost() {
        assertNull(hosts("0.0.0.0 com"))
        assertNull(hosts("0.0.0.0 1.2.3.4"))
        assertNull(hosts("0.0.0.0"))
    }

    @Test fun adblockBasicRule() {
        assertEquals("ads.example.com", adblock("||ads.example.com^"))
        assertEquals("ads.example.com", adblock("  ||Ads.Example.com^  "))
    }

    @Test fun adblockSkipsNonDomainRules() {
        assertNull(adblock("! comment"))
        assertNull(adblock("[Adblock Plus]"))
        assertNull(adblock("@@||allowed.example.com^"))
        assertNull(adblock("||ads.example.com^\$third-party"))
        assertNull(adblock("||example.com/path^"))
        assertNull(adblock("||*.example.com^"))
        assertNull(adblock("example.com"))
    }

    @Test fun invalidLabelsAreRejected() {
        assertNull(ListParser.normalizeDomain("-bad.example.com"))
        assertNull(ListParser.normalizeDomain("bad-.example.com"))
        assertNull(ListParser.normalizeDomain("a..example.com"))
        assertNull(ListParser.normalizeDomain("exa mple.com"))
        assertNull(ListParser.normalizeDomain("${"a".repeat(64)}.example.com"))
        assertNull(ListParser.normalizeDomain("${"a.".repeat(130)}com"))
    }

    @Test fun validDomainsAreAccepted() {
        assertEquals("xn--bcher-kva.example", ListParser.normalizeDomain("xn--bcher-kva.example"))
        assertEquals("_dmarc.example.com", ListParser.normalizeDomain("_dmarc.example.com"))
        assertEquals("t.co", ListParser.normalizeDomain("t.co"))
    }

    @Test fun malformedInputNeverThrows() {
        val junk = listOf("||", "||^", "|", "^", "\u0000", "0.0.0.0 \u0000", "||\u0000^", "\t\t", "#")
        for (line in junk) {
            for (format in ListFormat.entries) {
                ListParser.parseLine(line, format) // must not throw
            }
        }
        assertNull(adblock("||^"))
    }
}
