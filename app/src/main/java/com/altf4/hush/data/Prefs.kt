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

    /** Quiet window as minutes after midnight. */
    val nightStartMinutes: Int get() = sp.getInt(K_NIGHT_START, DEFAULT_NIGHT_START)
    val nightEndMinutes: Int get() = sp.getInt(K_NIGHT_END, DEFAULT_NIGHT_END)

    fun setNightWindow(startMinutes: Int, endMinutes: Int) = edit(bump = true) {
        putInt(K_NIGHT_START, startMinutes)
        putInt(K_NIGHT_END, endMinutes)
    }

    /** Master switch for the quiet window (app closing and quiet-hours-only sites). Off by default. */
    var nightEnabled: Boolean
        get() = sp.getBoolean(K_NIGHT_ON, false)
        set(value) = edit(bump = true) { putBoolean(K_NIGHT_ON, value) }

    /** Days the window starts on, as a bit mask (bit 0 = Monday ... bit 6 = Sunday). Every day by default. */
    var nightDays: Int
        get() = sp.getInt(K_NIGHT_DAYS, Schedule.ALL_DAYS)
        set(value) = edit(bump = true) { putInt(K_NIGHT_DAYS, value and Schedule.ALL_DAYS) }

    fun schedule(): Schedule = Schedule(nightEnabled, nightStartMinutes, nightEndMinutes, nightDays)

    /** Package names of the apps closed during quiet hours. A setting the person chose, not usage data. */
    fun blockedApps(): Set<String> = stringSet(K_APPS)

    fun setAppBlocked(pkg: String, blocked: Boolean) = changeSet(K_APPS, pkg, blocked)

    fun setBlockedApps(packages: Set<String>) =
        edit(bump = true) { putStringSet(K_APPS, HashSet(packages)) }

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

    /** Sites the user always blocks, as clean domains (see [DomainInput]). */
    fun customBlocked(): Set<String> = stringSet(K_CUSTOM)

    fun setCustomBlocked(domain: String, present: Boolean) = changeSet(K_CUSTOM, domain, present)

    /** Sites that stay reachable even if a list or a custom rule blocks them. */
    fun allowed(): Set<String> = stringSet(K_ALLOWED)

    fun setAllowed(domain: String, present: Boolean) = changeSet(K_ALLOWED, domain, present)

    /** Patterns (see SafeRegex) that block matching names. */
    fun regexBlock(): Set<String> = stringSet(K_REGEX_BLOCK)

    fun setRegexBlock(pattern: String, present: Boolean) = changeSet(K_REGEX_BLOCK, pattern, present)

    /** Patterns that keep matching names reachable, overriding lists and block patterns. */
    fun regexAllow(): Set<String> = stringSet(K_REGEX_ALLOW)

    fun setRegexAllow(pattern: String, present: Boolean) = changeSet(K_REGEX_ALLOW, pattern, present)

    /** Block names whose CNAME chain reaches a blocked domain (disguised trackers). On by default. */
    var uncloakCname: Boolean
        get() = sp.getBoolean(K_UNCLOAK, true)
        set(value) = edit(bump = true) { putBoolean(K_UNCLOAK, value) }

    /** Block public names that answer with private addresses (DNS rebinding). On by default. */
    var blockRebinding: Boolean
        get() = sp.getBoolean(K_REBINDING, true)
        set(value) = edit(bump = true) { putBoolean(K_REBINDING, value) }

    /** Block well-known encrypted-DNS provider names and the Firefox opt-out signal. Off by default. */
    var blockBypass: Boolean
        get() = sp.getBoolean(K_BYPASS, false)
        set(value) = edit(bump = true) { putBoolean(K_BYPASS, value) }

    /** Opt-in weekly list update (charging + unmetered network only). Off by default. */
    var weeklyUpdate: Boolean
        get() = sp.getBoolean(K_WEEKLY, false)
        set(value) = edit(bump = false) { putBoolean(K_WEEKLY, value) }

    /** When the lists were last downloaded (millis), or 0 if never. Shown in the UI. */
    val listsUpdatedAt: Long get() = sp.getLong(K_LISTS_UPDATED, 0L)

    /** Number of unique domains in the downloaded lists. */
    val listsEntryCount: Int get() = sp.getInt(K_LISTS_COUNT, 0)

    /** SHA-256 (hex) of the last download of a catalog list, or null. */
    fun listSha(id: String): String? = sp.getString(K_SHA_PREFIX + id, null)

    /** Records a finished update and bumps the version so the service reloads. */
    fun commitListUpdate(updatedAt: Long, count: Int, shas: Map<String, String>) =
        edit(bump = true) {
            putLong(K_LISTS_UPDATED, updatedAt)
            putInt(K_LISTS_COUNT, count)
            for (source in ListCatalog.all) remove(K_SHA_PREFIX + source.id)
            for ((id, sha) in shas) putString(K_SHA_PREFIX + id, sha)
        }

    private fun stringSet(key: String): Set<String> =
        HashSet(sp.getStringSet(key, null) ?: emptySet())

    private fun changeSet(key: String, value: String, present: Boolean) {
        val next = HashSet(stringSet(key))
        if (present) next.add(value) else next.remove(value)
        edit(bump = true) { putStringSet(key, next) }
    }

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
        const val K_NIGHT_ON = "night_enabled"
        const val K_NIGHT_DAYS = "night_days"
        const val K_APPS = "blocked_apps"
        const val K_LISTS = "lists_enabled"
        const val K_SOCIAL_PREFIX = "social_"
        const val K_CUSTOM = "custom_blocked"
        const val K_ALLOWED = "allowed"
        const val K_WEEKLY = "weekly_update"
        const val K_REGEX_BLOCK = "regex_block"
        const val K_REGEX_ALLOW = "regex_allow"
        const val K_UNCLOAK = "uncloak_cname"
        const val K_REBINDING = "block_rebinding"
        const val K_BYPASS = "block_bypass"
        const val K_LISTS_UPDATED = "lists_updated_at"
        const val K_LISTS_COUNT = "lists_count"
        const val K_SHA_PREFIX = "sha_"
        const val DEFAULT_NIGHT_START = 23 * 60
        const val DEFAULT_NIGHT_END = 6 * 60
    }
}
