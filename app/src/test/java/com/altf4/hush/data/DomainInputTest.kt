package com.altf4.hush.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DomainInputTest {
    @Test fun plainDomainsAreNormalised() {
        assertEquals("example.com", DomainInput.parse("example.com"))
        assertEquals("example.com", DomainInput.parse("  Example.COM  "))
        assertEquals("example.com", DomainInput.parse("example.com."))
        assertEquals("m.example.com", DomainInput.parse("m.example.com"))
    }

    @Test fun linksAreReducedToTheirHost() {
        assertEquals("example.com", DomainInput.parse("https://www.example.com/path?x=1#y"))
        assertEquals("sub.example.com", DomainInput.parse("http://user:pw@sub.example.com:8080/"))
        assertEquals("example.com", DomainInput.parse("example.com/some/page"))
        assertEquals("example.com", DomainInput.parse("HTTPS://EXAMPLE.COM"))
    }

    @Test fun leadingWwwIsDroppedOnlyWhenADomainRemains() {
        assertEquals("example.com", DomainInput.parse("www.example.com"))
        assertEquals("www.com", DomainInput.parse("www.com"))
    }

    @Test fun invalidInputIsRejected() {
        assertNull(DomainInput.parse(""))
        assertNull(DomainInput.parse("   "))
        assertNull(DomainInput.parse("https://"))
        assertNull(DomainInput.parse("localhost"))
        assertNull(DomainInput.parse("com"))
        assertNull(DomainInput.parse("not a domain"))
        assertNull(DomainInput.parse("192.168.0.1"))
        assertNull(DomainInput.parse("-bad.example.com"))
        assertNull(DomainInput.parse("ex*mple.com"))
    }
}
