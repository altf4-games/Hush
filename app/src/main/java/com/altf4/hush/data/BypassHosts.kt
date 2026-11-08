package com.altf4.hush.data

/**
 * Names that let a browser or app send DNS around Hush: the signal that makes Firefox switch its
 * secure-DNS feature off, and the hostnames of well-known public encrypted-DNS providers.
 * Optional and off by default. It cannot stop a client that connects to a provider by IP address
 * or an Android Private DNS setting that is not hostname-based.
 */
object BypassHosts {
    val all: List<String> = listOf(
        // Firefox treats NXDOMAIN for this name as "the network filters DNS; do not use secure DNS".
        "use-application-dns.net",
        "dns.google",
        "cloudflare-dns.com",
        "one.one.one.one",
        "dns.quad9.net",
        "dns9.quad9.net",
        "dns10.quad9.net",
        "dns11.quad9.net",
        "doh.opendns.com",
        "dns.nextdns.io",
        "doh.dns.sb",
        "dns.adguard.com",
        "dns.adguard-dns.com",
        "doh.cleanbrowsing.org",
        "dns.digitale-gesellschaft.ch",
        "doh.libredns.gr",
    )
}
