package com.altf4.hush.dns

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import com.altf4.hush.data.Prefs

/**
 * Restarts site blocking after a reboot or an app update, but only if it was on and the system
 * still has the person's VPN permission for us (no consent screen can be shown from here).
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return
        val app = context.applicationContext
        if (!Prefs(app).protectionWanted) return
        if (VpnService.prepare(app) != null) return // permission gone; wait for the person
        app.startService(Intent(app, DnsVpnService::class.java))
    }
}
