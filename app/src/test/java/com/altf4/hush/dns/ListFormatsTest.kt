package com.altf4.hush.dns

import com.altf4.hush.data.BypassHosts
import java.io.BufferedReader
import java.io.StringReader
import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ListFormatsTest {
    private fun domains(line: String) = ListParser.parseLine(line, ListFormat.Domains)

    @Test fun domainLinesAreParsed() {
        assertEquals("example.com", domains("example.com"))
        assertEquals("example.com", domains("  Example.COM  # note"))
        assertEquals("example.com", domains("*.example.com"))
    }

    @Test fun nonDomainLinesAreSkipped() {
        assertNull(domains(""))
        assertNull(domains("# comment"))
        assertNull(domains("two words"))
        assertNull(domains("localhost"))
        assertNull(domains("1.2.3.4"))
        assertNull(domains("-bad.example.com"))
        assertNull(domains("http://example.com/path"))
    }

    @Test fun collectCountsDomainsFormat() {
        val sink = HashBuffer(16)
        val text = "# Phishing list\nexample.com\n\nfoo.example.org\nexample.com\n"
        val accepted = ListParser.collect(BufferedReader(StringReader(text)), ListFormat.Domains, sink)
        assertEquals(3, accepted)
        assertEquals(2, sink.sortedUnique().size)
    }

    @Test fun everyCategoryHasAtLeastOneList() {
        for (category in ListCategory.entries) {
            assertTrue(category.name, ListCatalog.inCategory(category).isNotEmpty())
        }
    }

    @Test fun defaultsCoverAdsTrackingAndThreats() {
        val categories = ListCatalog.all
            .filter { it.id in ListCatalog.defaultEnabledIds }
            .map { it.category }
            .toSet()
        assertTrue(ListCategory.Ads in categories)
        assertTrue(ListCategory.Tracking in categories)
        assertTrue(ListCategory.Security in categories)
    }

    @Test fun catalogUrlsAreHttpsWithAHostAndUnique() {
        val hosts = ListCatalog.all.map { URI(it.url) }
        for (uri in hosts) {
            assertEquals("https", uri.scheme)
            assertTrue(uri.host.isNotBlank())
        }
        assertEquals(ListCatalog.all.size, ListCatalog.all.map { it.url }.toSet().size)
    }

    @Test fun bypassHostsAreValidUniqueAndIncludeTheFirefoxSignal() {
        for (host in BypassHosts.all) {
            assertEquals(host, ListParser.normalizeDomain(host))
        }
        assertEquals(BypassHosts.all.size, BypassHosts.all.toSet().size)
        assertTrue("use-application-dns.net" in BypassHosts.all)
    }

    @Test fun bypassHostsBlockTheirSubdomainsAndNothingElse() {
        val list = Blocklist.build(BypassHosts.all)
        fun blocks(n: String): Boolean {
            val b = n.toByteArray(Charsets.US_ASCII)
            return list.matches(b, b.size)
        }
        assertTrue(blocks("use-application-dns.net"))
        assertTrue(blocks("x.dns.google"))
        assertFalse(blocks("google.com"))
        assertFalse(blocks("www.google.com"))
        assertFalse(blocks("quad9.net"))
    }
}
