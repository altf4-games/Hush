package com.altf4.hush.block

/** The one decision the app blocker makes. Pure, so it can be tested without a device. */
object BlockDecision {
    /**
     * True if a window change in [pkg] should be turned into "go home": it must be one of the
     * chosen apps, never this app, the quiet window must be active, and the app must not hold an
     * emergency pass. The cheap checks come first; [nightActive] and [hasPass] are only asked for
     * a chosen app.
     */
    fun shouldBlock(
        pkg: String?,
        ownPackage: String,
        blocked: Set<String>,
        hasPass: (String) -> Boolean = { false },
        nightActive: () -> Boolean,
    ): Boolean {
        if (pkg == null || pkg == ownPackage) return false
        if (pkg !in blocked) return false
        if (!nightActive()) return false
        return !hasPass(pkg)
    }
}
