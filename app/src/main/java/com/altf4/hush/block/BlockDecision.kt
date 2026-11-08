package com.altf4.hush.block

/** The one decision the app blocker makes. Pure, so it can be tested without a device. */
object BlockDecision {
    /**
     * True if a window change in [pkg] should be turned into "go home": it must be one of the
     * chosen apps, never this app, and the quiet window must be active. The cheap checks come
     * first; [nightActive] is only asked for a chosen app.
     */
    fun shouldBlock(
        pkg: String?,
        ownPackage: String,
        blocked: Set<String>,
        nightActive: () -> Boolean,
    ): Boolean {
        if (pkg == null || pkg == ownPackage) return false
        if (pkg !in blocked) return false
        return nightActive()
    }
}
