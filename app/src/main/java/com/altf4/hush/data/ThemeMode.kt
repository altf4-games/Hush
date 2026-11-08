package com.altf4.hush.data

/** User's theme choice. Dark is the default, including for unknown stored values. */
enum class ThemeMode(val key: String, val label: String) {
    Dark("dark", "Dark"),
    Light("light", "Light"),
    System("system", "System");

    companion object {
        fun fromKey(key: String?): ThemeMode = entries.firstOrNull { it.key == key } ?: Dark
    }
}
