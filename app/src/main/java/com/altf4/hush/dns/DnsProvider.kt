package com.altf4.hush.dns

import java.net.InetAddress

/**
 * The encrypted upstream resolvers a person can pick. Each is reached over DNS-over-TLS at fixed
 * addresses, and its certificate must be valid for [hostname], so no name is ever looked up in
 * the clear to find it. Fixed in code like the list catalog: never read servers from elsewhere.
 */
enum class DnsProvider(
    val key: String,
    val label: String,
    val description: String,
    val hostname: String,
    private val addresses: List<ByteArray>,
) {
    Google(
        key = "google",
        label = "Google",
        description = "Fast and reliable almost everywhere.",
        hostname = "dns.google",
        addresses = listOf(byteArrayOf(8, 8, 8, 8), byteArrayOf(8, 8, 4, 4)),
    ),
    Cloudflare(
        key = "cloudflare",
        label = "Cloudflare",
        description = "Privacy-focused, with logs wiped within a day.",
        hostname = "cloudflare-dns.com",
        addresses = listOf(byteArrayOf(1, 1, 1, 1), byteArrayOf(1, 0, 0, 1)),
    ),
    Quad9(
        key = "quad9",
        label = "Quad9",
        description = "Non-profit, and blocks known malicious domains.",
        hostname = "dns.quad9.net",
        addresses = listOf(byteArrayOf(9, 9, 9, 9), byteArrayOf(149.toByte(), 112, 112, 112)),
    );

    /** Primary and secondary server, tried in order. */
    val servers: List<DotServer>
        get() = addresses.map { DotServer(InetAddress.getByAddress(it), PORT, hostname) }

    /** The addresses as dotted text, for the About screen. */
    val addressText: String
        get() = addresses.joinToString(" and ") { a -> a.joinToString(".") { (it.toInt() and 0xFF).toString() } }

    companion object {
        const val PORT = 853
        val DEFAULT = Google

        fun fromKey(key: String?): DnsProvider = entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}
