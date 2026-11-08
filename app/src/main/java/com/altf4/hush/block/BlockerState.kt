package com.altf4.hush.block

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Whether the accessibility service is connected. Observable by the UI; written only by [AppBlockerService]. */
object BlockerState {
    var running by mutableStateOf(false)
        private set

    fun update(value: Boolean) {
        running = value
    }
}
