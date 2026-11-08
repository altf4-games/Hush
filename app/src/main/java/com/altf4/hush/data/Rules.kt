package com.altf4.hush.data

import com.altf4.hush.dns.Blocklist

/** Turns settings into the domain set the DNS service blocks. */
object Rules {
    /**
     * Domains of every social preset set to [SocialMode.Always]. Quiet-hours-only presets are not
     * included until the schedule is wired in (Phase 3).
     */
    fun alwaysBlockedDomains(modeOf: (String) -> SocialMode): List<String> {
        val domains = ArrayList<String>()
        for (site in SocialSites.all) {
            if (modeOf(site.id) == SocialMode.Always) domains.addAll(site.domains)
        }
        return domains
    }

    fun build(prefs: Prefs): Blocklist =
        Blocklist.build(alwaysBlockedDomains { prefs.socialMode(it) })
}
