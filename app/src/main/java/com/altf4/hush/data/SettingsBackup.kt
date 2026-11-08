package com.altf4.hush.data

import com.altf4.hush.dns.ListCatalog
import com.altf4.hush.dns.SafeRegex

/** Every setting that goes into a backup file. Nothing else (no counters, no list contents). */
data class SettingsSnapshot(
    val theme: ThemeMode,
    val nightEnabled: Boolean,
    val nightStart: Int,
    val nightEnd: Int,
    val nightDays: Int,
    val strict: Boolean,
    val apps: Set<String>,
    val social: Map<String, SocialMode>,
    val custom: Set<String>,
    val allowed: Set<String>,
    val regexBlock: Set<String>,
    val regexAllow: Set<String>,
    val lists: Set<String>,
    val uncloak: Boolean,
    val rebinding: Boolean,
    val bypass: Boolean,
    val weekly: Boolean,
)

/** The outcome of reading a backup file: a snapshot, or one short reason it was refused. */
sealed class BackupResult {
    class Ok(val snapshot: SettingsSnapshot) : BackupResult()
    class Refused(val reason: String) : BackupResult()
}

/**
 * Plain-text backup of the settings. A file is untrusted input, so reading it is strict: it must
 * be complete, every value must pass the same checks as typing it in, and anything unexpected
 * refuses the whole file (nothing is half-applied).
 *
 * Format: first line `hush-settings 1`, then one `key<TAB>value` line per setting; list-like
 * settings repeat their key. Values may not contain tabs or line breaks.
 */
object SettingsBackup {
    const val HEADER = "hush-settings 1"
    const val MAX_CHARS = 512 * 1024
    const val MAX_APPS = 2000
    const val MAX_DOMAINS = 5000

    private val PACKAGE = Regex("""[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+""")
    private const val MAX_PACKAGE_LENGTH = 255

    private val SCALARS = listOf(
        "theme", "night_enabled", "night_start", "night_end", "night_days", "strict",
        "uncloak", "rebinding", "bypass", "weekly",
    )

    fun encode(s: SettingsSnapshot): String {
        val out = StringBuilder(HEADER).append('\n')
        fun line(key: String, value: String) {
            if (value.any { it == '\t' || it == '\n' || it == '\r' }) return // cannot be written safely
            out.append(key).append('\t').append(value).append('\n')
        }
        fun flag(key: String, on: Boolean) = line(key, if (on) "1" else "0")

        line("theme", s.theme.key)
        flag("night_enabled", s.nightEnabled)
        line("night_start", s.nightStart.toString())
        line("night_end", s.nightEnd.toString())
        line("night_days", s.nightDays.toString())
        flag("strict", s.strict)
        flag("uncloak", s.uncloak)
        flag("rebinding", s.rebinding)
        flag("bypass", s.bypass)
        flag("weekly", s.weekly)
        for (id in s.social.keys.sorted()) line("social", "$id:${s.social.getValue(id).key}")
        for (v in s.lists.sorted()) line("list", v)
        for (v in s.apps.sorted()) line("app", v)
        for (v in s.custom.sorted()) line("custom", v)
        for (v in s.allowed.sorted()) line("allowed", v)
        for (v in s.regexBlock.sorted()) line("regex_block", v)
        for (v in s.regexAllow.sorted()) line("regex_allow", v)
        return out.toString()
    }

    fun decode(text: String): BackupResult {
        if (text.length > MAX_CHARS) return refuse("The file is too large")
        val lines = text.split('\n').map { it.trimEnd('\r') }.filter { it.isNotEmpty() }
        if (lines.firstOrNull() != HEADER) return refuse("This is not a Hush settings file")

        val scalars = HashMap<String, String>()
        val social = LinkedHashMap<String, SocialMode>()
        val lists = LinkedHashSet<String>()
        val apps = LinkedHashSet<String>()
        val custom = LinkedHashSet<String>()
        val allowed = LinkedHashSet<String>()
        val regexBlock = LinkedHashSet<String>()
        val regexAllow = LinkedHashSet<String>()

        for (raw in lines.drop(1)) {
            val tab = raw.indexOf('\t')
            if (tab <= 0) return refuse("A line could not be read")
            val key = raw.substring(0, tab)
            val value = raw.substring(tab + 1)
            if (value.isEmpty() || value.indexOf('\t') >= 0) return refuse("A value could not be read")
            when (key) {
                in SCALARS -> if (scalars.put(key, value) != null) return refuse("A setting appears twice")
                "social" -> {
                    val colon = value.indexOf(':')
                    if (colon <= 0) return refuse("A site choice could not be read")
                    val id = value.substring(0, colon)
                    val mode = SocialMode.entries.firstOrNull { it.key == value.substring(colon + 1) }
                    if (SocialSites.all.none { it.id == id } || mode == null) return refuse("An unknown site choice")
                    if (social.put(id, mode) != null) return refuse("A site choice appears twice")
                }
                "list" -> {
                    if (ListCatalog.all.none { it.id == value }) return refuse("An unknown blocklist")
                    lists.add(value)
                }
                "app" -> {
                    if (value.length > MAX_PACKAGE_LENGTH || !PACKAGE.matches(value)) return refuse("An app name is not valid")
                    if (apps.size >= MAX_APPS) return refuse("Too many apps")
                    apps.add(value)
                }
                "custom", "allowed" -> {
                    if (DomainInput.parse(value) != value) return refuse("A site is not a clean domain")
                    val target = if (key == "custom") custom else allowed
                    if (target.size >= MAX_DOMAINS) return refuse("Too many sites")
                    target.add(value)
                }
                "regex_block", "regex_allow" -> {
                    if (SafeRegex.check(value) != null) return refuse("A pattern was refused")
                    val target = if (key == "regex_block") regexBlock else regexAllow
                    if (target.size >= SafeRegex.MAX_RULES) return refuse("Too many patterns")
                    target.add(value)
                }
                else -> return refuse("An unknown setting")
            }
        }
        if (SCALARS.any { it !in scalars }) return refuse("The file is incomplete")

        val theme = ThemeMode.entries.firstOrNull { it.key == scalars["theme"] }
            ?: return refuse("An unknown theme")
        val start = scalars.getValue("night_start").toIntOrNull()
        val end = scalars.getValue("night_end").toIntOrNull()
        val days = scalars.getValue("night_days").toIntOrNull()
        if (start == null || end == null || start !in 0 until Schedule.MINUTES_PER_DAY ||
            end !in 0 until Schedule.MINUTES_PER_DAY
        ) {
            return refuse("The night window is not valid")
        }
        if (days == null || days !in 0..Schedule.ALL_DAYS) return refuse("The night days are not valid")

        fun flag(key: String): Boolean? = when (scalars.getValue(key)) {
            "1" -> true
            "0" -> false
            else -> null
        }
        val night = flag("night_enabled")
        val strict = flag("strict")
        val uncloak = flag("uncloak")
        val rebinding = flag("rebinding")
        val bypass = flag("bypass")
        val weekly = flag("weekly")
        if (night == null || strict == null || uncloak == null || rebinding == null || bypass == null || weekly == null) {
            return refuse("A switch has an unexpected value")
        }

        // A preset that is not mentioned stays off.
        val allSocial = SocialSites.all.associate { it.id to (social[it.id] ?: SocialMode.Off) }

        return BackupResult.Ok(
            SettingsSnapshot(
                theme = theme,
                nightEnabled = night,
                nightStart = start,
                nightEnd = end,
                nightDays = days,
                strict = strict,
                apps = apps,
                social = allSocial,
                custom = custom,
                allowed = allowed,
                regexBlock = regexBlock,
                regexAllow = regexAllow,
                lists = lists,
                uncloak = uncloak,
                rebinding = rebinding,
                bypass = bypass,
                weekly = weekly,
            ),
        )
    }

    private fun refuse(reason: String): BackupResult = BackupResult.Refused(reason)
}
