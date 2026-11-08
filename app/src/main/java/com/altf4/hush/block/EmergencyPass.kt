package com.altf4.hush.block

/**
 * The emergency pass: a short, deliberate way back into one chosen app during quiet hours.
 *
 * Flow: ask for a pass (this starts a [WAIT_MS] wait), come back after the wait and open the app
 * again, then take the pass, which lets that one app through for [PASS_MS]. An unused request
 * lapses after [REQUEST_TTL_MS]. Only one request and one pass exist at a time. Strict mode turns
 * the whole thing off.
 *
 * Times are monotonic milliseconds passed in by the caller (no clock reads, no timers). State
 * lives in memory only: it is gone when the process ends, which errs on the side of blocking.
 * The package name held here is an app the person chose to block; it is never written to disk.
 */
class EmergencyPass {
    enum class Phase { Unavailable, CanRequest, Waiting, Ready }

    private var requestPackage: String? = null
    private var requestedAt = 0L
    private var passPackage: String? = null
    private var passUntil = 0L

    @Synchronized
    fun phase(pkg: String, strict: Boolean, now: Long): Phase {
        if (strict) return Phase.Unavailable
        if (!hasLiveRequest(pkg, now)) return Phase.CanRequest
        return if (now - requestedAt >= WAIT_MS) Phase.Ready else Phase.Waiting
    }

    /** Milliseconds left of the wait for [pkg], or 0 if there is none. */
    @Synchronized
    fun waitLeft(pkg: String, now: Long): Long {
        if (!hasLiveRequest(pkg, now)) return 0L
        return (WAIT_MS - (now - requestedAt)).coerceAtLeast(0L)
    }

    /** Starts the wait. Asking again while a request is live does not restart it. */
    @Synchronized
    fun request(pkg: String, strict: Boolean, now: Long) {
        if (strict || hasLiveRequest(pkg, now)) return
        requestPackage = pkg
        requestedAt = now
    }

    /** Takes the pass if the wait is over. Returns true if [pkg] may now be opened. */
    @Synchronized
    fun grant(pkg: String, strict: Boolean, now: Long): Boolean {
        if (phase(pkg, strict, now) != Phase.Ready) return false
        requestPackage = null
        passPackage = pkg
        passUntil = now + PASS_MS
        return true
    }

    @Synchronized
    fun isActive(pkg: String, now: Long): Boolean = pkg == passPackage && now < passUntil

    /** Drops everything, e.g. when strict mode is switched on. */
    @Synchronized
    fun clear() {
        requestPackage = null
        passPackage = null
    }

    private fun hasLiveRequest(pkg: String, now: Long): Boolean =
        pkg == requestPackage && now - requestedAt < REQUEST_TTL_MS

    companion object {
        const val WAIT_MS = 60_000L
        const val PASS_MS = 5L * 60_000L
        const val REQUEST_TTL_MS = 10L * 60_000L

        /** The one shared instance; the service and the block screen run in the same process. */
        val shared = EmergencyPass()
    }
}
