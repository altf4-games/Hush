package com.altf4.hush.data

import android.content.Context
import android.content.SharedPreferences
import com.altf4.hush.dns.ListCatalog

/**
 * Settings store. Services re-read settings only when [version] changes, so every change that
 * affects blocking bumps it. Appearance does not.
 */
class Prefs(context: Context) {
    private val sp: SharedPreferences =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    val version: Int get() = sp.getInt(K_VERSION, 0)

    var themeMode: ThemeMode
        get() = ThemeMode.fromKey(sp.getString(K_THEME, null))
        set(value) = edit(bump = false) { putString(K_THEME, value.key) }

    /** Quiet window as minutes after midnight. Display only for now; editing comes later. */
    val nightStartMinutes: Int get() = sp.getInt(K_NIGHT_START, DEFAULT_NIGHT_START)
    val nightEndMinutes: Int get() = sp.getInt(K_NIGHT_END, DEFAULT_NIGHT_END)

    fun enabledLists(): Set<String> {
        val stored = sp.getStringSet(K_LISTS, null) ?: return ListCatalog.defaultEnabledIds
        return HashSet(stored) // copy: the set returned by SharedPreferences must not be mutated
    }

    fun setListEnabled(id: String, enabled: Boolean) {
        val next = HashSet(enabledLists())
        if (enabled) next.add(id) else next.remove(id)
        edit(bump = true) { putStringSet(K_LISTS, next) }
    }

    fun socialMode(id: String): SocialMode =
        SocialMode.fromKey(sp.getString(K_SOCIAL_PREFIX + id, null))

    fun setSocialMode(id: String, mode: SocialMode) =
        edit(bump = true) { putString(K_SOCIAL_PREFIX + id, mode.key) }

    /**
     * Calls [onChange] whenever a blocking-related setting changes (the version counter moves).
     * Event-driven: no polling. Close the returned handle to stop listening.
     */
    fun registerVersionListener(onChange: () -> Unit): AutoCloseable {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == K_VERSION) onChange()
        }
        sp.registerOnSharedPreferenceChangeListener(listener)
        return AutoCloseable { sp.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    private inline fun edit(bump: Boolean, block: SharedPreferences.Editor.() -> Unit) {
        val editor = sp.edit()
        editor.block()
        if (bump) editor.putInt(K_VERSION, sp.getInt(K_VERSION, 0) + 1)
        editor.apply()
    }

    private companion object {
        const val FILE = "hush_prefs"
        const val K_VERSION = "version"
        const val K_THEME = "theme"
        const val K_NIGHT_START = "night_start"
        const val K_NIGHT_END = "night_end"
        const val K_LISTS = "lists_enabled"
        const val K_SOCIAL_PREFIX = "social_"
        const val DEFAULT_NIGHT_START = 23 * 60
        const val DEFAULT_NIGHT_END = 6 * 60
    }
}
