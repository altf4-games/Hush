package com.altf4.hush.dns

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Observable state of the list update, for the UI. Written only by [ListUpdater]. */
object UpdateStatus {
    var running by mutableStateOf(false)
        private set

    /** Result of the last run, e.g. "Updated: 81,234 domains". Null before the first run. */
    var message by mutableStateOf<String?>(null)
        private set

    fun begin() {
        running = true
        message = null
    }

    fun finish(text: String) {
        message = text
        running = false
    }
}
