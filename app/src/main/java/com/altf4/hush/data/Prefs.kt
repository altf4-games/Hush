package com.altf4.hush.data

import android.content.Context
import android.content.SharedPreferences
import com.altf4.hush.dns.DnsProvider
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

    /**
     * Whether protection was on when the phone last ran, so it can be restarted after a reboot.
     * Device state, not a setting: it is not part of backups and does not bump [version].
     */
    var protectionWanted: Boolean
        get() = sp.getBoolean(K_PROTECTION, false)
        set(value) = edit(bump = false) { putBoolean(K_PROTECTION, value) }

    /** Encrypted upstream resolver. Google by default; changing it bumps [version] so the tunnel switches. */
    var dnsProvider: DnsProvider
        get() = DnsProvider.fromKey(sp.getString(K_DNS, null))
        set(value) = edit(bump = true) { putString(K_DNS, value.key) }

    /** Take the accent colour from the wallpaper (Android 12+). Appearance only: no version bump. */
    var dynamicColor: Boolean
        get() = sp.getBoolean(K_DYNAMIC, false)
        set(value) = edit(bump = false) { putBoolean(K_DYNAMIC, value) }

    /** True once the first-run setup screens have been finished or skipped. */
    var onboardingDone: Boolean
        get() = sp.getBoolean(K_ONBOARDED, false)
        set(value) = edit(bump = false) { putBoolean(K_ONBOARDED, value) }

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

    fun setSocialMode(id: String, mode: SocialMode) {
        edit(bump = true) { putString(K_SOCIAL_PREFIX + id, mode.key) }
    }

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

    /** Weekly list update (charging + unmetered network only). On by default. */
    var weeklyUpdate: Boolean
        get() = sp.getBoolean(K_WEEKLY, true)
        set(value) = edit(bump = false) { putBoolean(K_WEEKLY, value) }

    /** When the lists were last downloaded (millis), or 0 if never. Shown in the UI. */
    val listsUpdatedAt: Long get() = sp.getLong(K_LISTS_UPDATED, 0L)

    /** True when the last download skipped some lists (first run only), so a retry is still due. */
    val listsIncomplete: Boolean get() = sp.getBoolean(K_LISTS_INCOMPLETE, false)

    /** Number of unique domains in the downloaded lists. */
    val listsEntryCount: Int get() = sp.getInt(K_LISTS_COUNT, 0)

    /** SHA-256 (hex) of the last download of a catalog list, or null. */
    fun listSha(id: String): String? = sp.getString(K_SHA_PREFIX + id, null)

    /** Entries each catalog list contributed to the last download, or 0 if unknown. */
    fun listCount(id: String): Int = sp.getInt(K_COUNT_PREFIX + id, 0)

    /** Saved dashboard counters (numbers only). Device state: not in backups, no version bump. */
    var statsRaw: String?
        get() = sp.getString(K_STATS, null)
        set(value) = edit(bump = false) { if (value == null) remove(K_STATS) else putString(K_STATS, value) }

    /** Records a finished update and bumps the version so the service reloads. */
    fun commitListUpdate(
        updatedAt: Long,
        count: Int,
        shas: Map<String, String>,
        counts: Map<String, Int> = emptyMap(),
        incomplete: Boolean = false,
    ) = edit(bump = true) {
        putLong(K_LISTS_UPDATED, updatedAt)
        putBoolean(K_LISTS_INCOMPLETE, incomplete)
        putInt(K_LISTS_COUNT, count)
        for (source in ListCatalog.all) {
            remove(K_SHA_PREFIX + source.id)
            remove(K_COUNT_PREFIX + source.id)
        }
        for ((id, sha) in shas) putString(K_SHA_PREFIX + id, sha)
        for ((id, n) in counts) putInt(K_COUNT_PREFIX + id, n)
    }

    /** Everything that goes into a backup file. */
    fun snapshot(): SettingsSnapshot = SettingsSnapshot(
        theme = themeMode,
        social = SocialSites.all.associate { it.id to socialMode(it.id) },
        custom = customBlocked(),
        allowed = allowed(),
        regexBlock = regexBlock(),
        regexAllow = regexAllow(),
        lists = enabledLists(),
        uncloak = uncloakCname,
        rebinding = blockRebinding,
        bypass = blockBypass,
        weekly = weeklyUpdate,
        dns = dnsProvider,
    )

    /**
     * Replaces every setting in one step (one version bump). The snapshot must come from
     * [SettingsBackup.decode], which has already checked every value.
     */
    fun restore(s: SettingsSnapshot) {
        edit(bump = true) {
            putString(K_THEME, s.theme.key)
            for ((id, mode) in s.social) putString(K_SOCIAL_PREFIX + id, mode.key)
            putStringSet(K_CUSTOM, HashSet(s.custom))
            putStringSet(K_ALLOWED, HashSet(s.allowed))
            putStringSet(K_REGEX_BLOCK, HashSet(s.regexBlock))
            putStringSet(K_REGEX_ALLOW, HashSet(s.regexAllow))
            putStringSet(K_LISTS, HashSet(s.lists))
            putBoolean(K_UNCLOAK, s.uncloak)
            putBoolean(K_REBINDING, s.rebinding)
            putBoolean(K_BYPASS, s.bypass)
            putBoolean(K_WEEKLY, s.weekly)
            putString(K_DNS, s.dns.key)
        }
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
        const val K_PROTECTION = "protection_wanted"
        const val K_STATS = "stats"
        const val K_DNS = "dns_provider"
        const val K_DYNAMIC = "dynamic_color"
        const val K_ONBOARDED = "onboarding_done"
        const val K_COUNT_PREFIX = "list_count_"
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
        const val K_LISTS_INCOMPLETE = "lists_incomplete"
        const val K_SHA_PREFIX = "sha_"
    }
}
