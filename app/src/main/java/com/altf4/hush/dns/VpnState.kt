package com.altf4.hush.dns

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Whether the DNS tunnel is up. Observable by the UI; written only by [DnsVpnService]. */
object VpnState {
    var running by mutableStateOf(false)
        private set

    fun update(value: Boolean) {
        running = value
    }
}
