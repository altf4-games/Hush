package com.altf4.hush.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RulesTest {
    @Test fun onlyAlwaysModeSitesAreIncluded() {
        val domains = Rules.alwaysBlockedDomains { id ->
            when (id) {
                "instagram" -> SocialMode.Always
                "tiktok" -> SocialMode.Night
                else -> SocialMode.Off
            }
        }
        assertTrue("instagram.com" in domains)
        assertTrue("cdninstagram.com" in domains)
        assertFalse("tiktok.com" in domains)
        assertFalse("reddit.com" in domains)
    }

    @Test fun customSitesAreAlwaysIncluded() {
        val domains = Rules.alwaysBlockedDomains({ SocialMode.Off }, listOf("example.com", "news.example.org"))
        assertTrue("example.com" in domains)
        assertTrue("news.example.org" in domains)
    }

    @Test fun nothingIsBlockedWhenEverythingIsOff() {
        assertTrue(Rules.alwaysBlockedDomains { SocialMode.Off }.isEmpty())
    }
}
