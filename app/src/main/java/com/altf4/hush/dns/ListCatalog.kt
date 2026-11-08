package com.altf4.hush.dns

enum class ListFormat {
    /** `0.0.0.0 domain` lines. */
    Hosts,

    /** `||domain^` rules (Adblock syntax). */
    Adblock,

    /** One domain per line. */
    Domains,
}

enum class ListCategory(val label: String) {
    Ads("Ads"),
    Tracking("Tracking"),
    Security("Threats"),
    Content("Site categories"),
}

data class BlockListSource(
    val id: String,
    val name: String,
    val description: String,
    val url: String,
    val format: ListFormat,
    val category: ListCategory = ListCategory.Ads,
)

/**
 * The only list URLs the app is allowed to contact. Fixed in code on purpose: never fetch list
 * locations from anywhere else (see CLAUDE.md hard rule 1). Every entry was checked to respond
 * over HTTPS and parse in its stated format.
 */
object ListCatalog {
    val all: List<BlockListSource> = listOf(
        // Ads
        BlockListSource(
            id = "stevenblack",
            name = "StevenBlack unified hosts",
            description = "Ads, trackers and malware. A popular all-round default. About 2 MB.",
            url = "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts",
            format = ListFormat.Hosts,
        ),
        BlockListSource(
            id = "oisd_small",
            name = "OISD small",
            description = "Curated to block ads and trackers without breaking sites. About 1 MB.",
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
            description = "Small and light, with the fewest false positives. Under 1 MB.",
            url = "https://o0.pages.dev/Lite/hosts.txt",
            format = ListFormat.Hosts,
        ),

        // Tracking
        BlockListSource(
            id = "blp_tracking",
            name = "Blocklist Project: Tracking",
            description = "Trackers and telemetry. About 4 MB.",
            url = "https://blocklistproject.github.io/Lists/tracking.txt",
            format = ListFormat.Hosts,
            category = ListCategory.Tracking,
        ),
        BlockListSource(
            id = "easyprivacy",
            name = "EasyPrivacy",
            description = "A well-known privacy list, domains only. Under 1 MB.",
            url = "https://v.firebog.net/hosts/Easyprivacy.txt",
            format = ListFormat.Domains,
            category = ListCategory.Tracking,
        ),

        // Threats
        BlockListSource(
            id = "phishing_army",
            name = "Phishing Army",
            description = "Known phishing domains. About 3 MB.",
            url = "https://phishing.army/download/phishing_army_blocklist.txt",
            format = ListFormat.Domains,
            category = ListCategory.Security,
        ),
        BlockListSource(
            id = "blp_scam",
            name = "Blocklist Project: Scams",
            description = "Scam and fraud sites. Under 1 MB.",
            url = "https://blocklistproject.github.io/Lists/scam.txt",
            format = ListFormat.Hosts,
            category = ListCategory.Security,
        ),
        BlockListSource(
            id = "antimalware",
            name = "Anti-Malware (DandelionSprout)",
            description = "Malware domains. Under 1 MB.",
            url = "https://raw.githubusercontent.com/DandelionSprout/adfilt/master/Alternate%20versions%20Anti-Malware%20List/AntiMalwareHosts.txt",
            format = ListFormat.Hosts,
            category = ListCategory.Security,
        ),

        // Site categories
        BlockListSource(
            id = "blp_gambling",
            name = "Gambling sites",
            description = "About 8 MB.",
            url = "https://blocklistproject.github.io/Lists/gambling.txt",
            format = ListFormat.Hosts,
            category = ListCategory.Content,
        ),
        BlockListSource(
            id = "blp_porn",
            name = "Adult sites",
            description = "A large list, about 26 MB. Update on Wi-Fi.",
            url = "https://blocklistproject.github.io/Lists/porn.txt",
            format = ListFormat.Hosts,
            category = ListCategory.Content,
        ),
        BlockListSource(
            id = "blp_piracy",
            name = "Piracy sites",
            description = "Small.",
            url = "https://blocklistproject.github.io/Lists/piracy.txt",
            format = ListFormat.Hosts,
            category = ListCategory.Content,
        ),
    )

    fun inCategory(category: ListCategory): List<BlockListSource> =
        all.filter { it.category == category }

    /** Enabled on first run. */
    val defaultEnabledIds: Set<String> = setOf("stevenblack", "blp_tracking", "phishing_army")
}
