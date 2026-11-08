package com.altf4.hush.dns

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ListCatalogTest {
    @Test fun idsAreUnique() {
        val ids = ListCatalog.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test fun allUrlsAreHttps() {
        // Cleartext is disabled app-wide; a plain http:// entry would also never connect.
        assertTrue(ListCatalog.all.all { it.url.startsWith("https://") })
    }

    @Test fun defaultsExistInCatalog() {
        val ids = ListCatalog.all.map { it.id }.toSet()
        assertTrue(ListCatalog.defaultEnabledIds.isNotEmpty())
        assertTrue(ids.containsAll(ListCatalog.defaultEnabledIds))
    }

    @Test fun entriesHaveNamesAndDescriptions() {
        assertTrue(ListCatalog.all.all { it.name.isNotBlank() && it.description.isNotBlank() })
    }
}
