package com.altf4.hush.block

/**
 * Apps that are usually needed at any hour. "Select all" leaves these unticked; they can still be
 * ticked by hand. Matching is by package name, so it is a best-effort list, topped up at run time
 * with the device's default messaging app.
 */
object EssentialApps {
    private val KNOWN = setOf(
        // Settings
        "com.android.settings",
        // Maps
        "com.google.android.apps.maps",
        // Clock and alarms
        "com.google.android.deskclock",
        "com.android.deskclock",
        "com.sec.android.app.clockpackage",
        // Messages
        "com.google.android.apps.messaging",
        "com.android.mms",
        "com.samsung.android.messaging",
        // Contacts and phone helpers
        "com.google.android.contacts",
        "com.android.contacts",
        "com.samsung.android.app.contacts",
    )

    fun isEssential(pkg: String, deviceDefaults: Set<String>): Boolean =
        pkg in KNOWN || pkg in deviceDefaults
}
