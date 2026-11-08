package com.altf4.hush.data

import com.altf4.hush.dns.ListParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SocialSitesTest {
    @Test fun idsAreUniqueAndNonBlank() {
        val ids = SocialSites.all.map { it.id }
        assertTrue(ids.all { it.isNotBlank() })
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test fun everySiteHasAName() {
        assertTrue(SocialSites.all.all { it.name.isNotBlank() })
    }

    @Test fun domainsAreValidAndAlreadyNormalised() {
        for (site in SocialSites.all) {
            assertTrue("${site.id} has no domains", site.domains.isNotEmpty())
            for (domain in site.domains) {
                assertEquals("$domain is not normalised", domain, ListParser.normalizeDomain(domain))
            }
        }
    }

    @Test fun noDomainIsClaimedByTwoSites() {
        val all = SocialSites.all.flatMap { it.domains }
        assertEquals(all.size, all.toSet().size)
    }

    @Test fun socialModeFallsBackToOff() {
        assertEquals(SocialMode.Off, SocialMode.fromKey(null))
        assertEquals(SocialMode.Off, SocialMode.fromKey("nonsense"))
        assertEquals(SocialMode.Night, SocialMode.fromKey("night"))
    }
}
