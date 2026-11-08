package com.altf4.hush.data

import android.content.Context
import com.altf4.hush.dns.Blocklist
import com.altf4.hush.dns.DomainFilter
import com.altf4.hush.dns.ListStore
import com.altf4.hush.dns.RegexRules

/** Turns settings and the downloaded lists into the filter the DNS service uses. */
object Rules {
    /**
     * Domains of every social preset set to [SocialMode.Always], plus the user's own always-blocked
     * sites. Quiet-hours-only presets are not included until the schedule is wired in (Phase 3).
     */
    fun alwaysBlockedDomains(
        modeOf: (String) -> SocialMode,
        custom: Collection<String> = emptyList(),
    ): List<String> {
        val domains = ArrayList<String>(custom)
        for (site in SocialSites.all) {
            if (modeOf(site.id) == SocialMode.Always) domains.addAll(site.domains)
        }
        return domains
    }

    fun build(context: Context, prefs: Prefs): DomainFilter {
        val own = ArrayList<String>(alwaysBlockedDomains({ prefs.socialMode(it) }, prefs.customBlocked()))
        if (prefs.blockBypass) own.addAll(BypassHosts.all)
        return DomainFilter(
            allow = Blocklist.build(prefs.allowed()),
            block = listOf(Blocklist.build(own), ListStore.load(context)),
            allowRegex = RegexRules.of(prefs.regexAllow()),
            blockRegex = RegexRules.of(prefs.regexBlock()),
            uncloakCname = prefs.uncloakCname,
            blockRebinding = prefs.blockRebinding,
        )
    }
}
