package com.altf4.hush.dns

enum class ListFormat {
    /** `0.0.0.0 domain` lines. */
    Hosts,

    /** `||domain^` rules (Adblock syntax). */
    Adblock,
}

data class BlockListSource(
    val id: String,
    val name: String,
    val description: String,
    val url: String,
    val format: ListFormat,
)

/**
 * The only list URLs the app is allowed to contact. Fixed in code on purpose: never fetch list
 * locations from anywhere else (see CLAUDE.md hard rule 1).
 */
object ListCatalog {
    val all: List<BlockListSource> = listOf(
        BlockListSource(
            id = "stevenblack",
            name = "StevenBlack unified hosts",
            description = "Ads, trackers and malware. A popular all-round default.",
            url = "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts",
            format = ListFormat.Hosts,
        ),
        BlockListSource(
            id = "oisd_small",
            name = "OISD small",
            description = "Curated to block ads and trackers without breaking sites.",
            url = "https://small.oisd.nl/",
            format = ListFormat.Adblock,
        ),
        BlockListSource(
            id = "adguard_dns",
            name = "AdGuard DNS filter",
            description = "Ads and trackers, maintained by AdGuard.",
            url = "https://adguardteam.github.io/AdGuardSDNSFilter/Filters/filter.txt",
            format = ListFormat.Adblock,
        ),
        BlockListSource(
            id = "peter_lowe",
            name = "Peter Lowe's list",
            description = "A short, conservative list of ad servers.",
            url = "https://pgl.yoyo.org/adservers/serverlist.php?hostformat=hosts&showintro=0&mimetype=plaintext",
            format = ListFormat.Hosts,
        ),
        BlockListSource(
            id = "onehosts_lite",
            name = "1Hosts Lite",
            description = "Small and light, with the fewest false positives.",
            url = "https://o0.pages.dev/Lite/hosts.txt",
            format = ListFormat.Hosts,
        ),
    )

    /** Enabled on first run. */
    val defaultEnabledIds: Set<String> = setOf("stevenblack")
}
