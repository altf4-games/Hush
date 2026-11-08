package com.altf4.hush.dns

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsProviderTest {
    @Test fun googleIsTheDefault() {
        assertEquals(DnsProvider.Google, DnsProvider.DEFAULT)
        assertEquals(DnsProvider.Google, DnsProvider.fromKey(null))
        assertEquals(DnsProvider.Google, DnsProvider.fromKey("nonsense"))
    }

    @Test fun keysRoundTrip() {
        for (p in DnsProvider.entries) assertEquals(p, DnsProvider.fromKey(p.key))
    }

    @Test fun everyProviderHasTwoTlsServersForItsOwnHostname() {
        for (p in DnsProvider.entries) {
            assertEquals(p.name, 2, p.servers.size)
            for (s in p.servers) {
                assertEquals(853, s.port)
                assertEquals(p.hostname, s.hostname)
            }
        }
    }

    @Test fun addressesAreTheWellKnownOnes() {
        assertEquals("8.8.8.8 and 8.8.4.4", DnsProvider.Google.addressText)
        assertEquals("1.1.1.1 and 1.0.0.1", DnsProvider.Cloudflare.addressText)
        assertTrue(DnsProvider.Quad9.addressText.startsWith("9.9.9.9"))
        assertEquals("149.112.112.112", DnsProvider.Quad9.servers[1].address.hostAddress)
    }
}
