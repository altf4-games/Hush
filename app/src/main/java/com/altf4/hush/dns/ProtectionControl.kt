package com.altf4.hush.dns

import android.content.Context
import android.content.Intent
import android.net.VpnService

/**
 * Turns site blocking on or off from outside the app (the home screen widget and the quick
 * settings tile). The VPN consent screen can only be shown by an activity, so when the system
 * does not yet hold the person's permission, starting is refused and the caller opens the app.
 */
object ProtectionControl {
    fun needsConsent(context: Context): Boolean = VpnService.prepare(context.applicationContext) != null

    /** Flips protection. Returns false only when turning on needs the consent screen first. */
    fun toggle(context: Context): Boolean {
        val app = context.applicationContext
        if (VpnState.running) {
            stop(app)
            return true
        }
        if (needsConsent(app)) return false
        app.startService(Intent(app, DnsVpnService::class.java))
        return true
    }

    private fun stop(app: Context) {
        app.startService(Intent(app, DnsVpnService::class.java).setAction(DnsVpnService.ACTION_STOP))
    }
}
