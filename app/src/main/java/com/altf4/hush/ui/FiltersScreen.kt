package com.altf4.hush.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.altf4.hush.data.DomainInput
import com.altf4.hush.data.Prefs
import com.altf4.hush.data.SocialMode
import com.altf4.hush.data.SocialSite
import com.altf4.hush.data.SocialSites
import com.altf4.hush.dns.BlockListSource
import com.altf4.hush.dns.ListCatalog
import com.altf4.hush.dns.ListCategory
import com.altf4.hush.dns.SafeRegex
import com.altf4.hush.dns.UpdateStatus
import java.text.DateFormat
import java.util.Date

private val TABS = listOf("Lists", "Rules", "Social")

/** What gets blocked: the downloaded lists, the person's own rules, and the social presets. */
@Composable
fun FiltersScreen(
    prefs: Prefs,
    onUpdateNow: () -> Unit,
    onWeeklyChange: (Boolean) -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(0) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenHeader("Filters", "Choose what Hush blocks.") }
        item { SegmentTabs(TABS, tab) { tab = it } }

        when (tab) {
            0 -> listsTab(prefs, onUpdateNow, onWeeklyChange)
            1 -> rulesTab(prefs)
            else -> socialTab(prefs)
        }

        item { Spacer(Modifier.height(8.dp)) }
    }
}

// --- Lists ---------------------------------------------------------------------------------

private fun androidx.compose.foundation.lazy.LazyListScope.listsTab(
    prefs: Prefs,
    onUpdateNow: () -> Unit,
    onWeeklyChange: (Boolean) -> Unit,
) {
    item(key = "lists-body") {
        var enabledLists by remember { mutableStateOf(prefs.enabledLists()) }
        var weekly by remember { mutableStateOf(prefs.weeklyUpdate) }
        val updatedAt = remember(UpdateStatus.running) { prefs.listsUpdatedAt }
        val entryCount = remember(UpdateStatus.running) { prefs.listsEntryCount }
        // The lists that went into the last download; a difference means choices are not applied yet.
        val applied = remember(UpdateStatus.running) {
            ListCatalog.all.filter { prefs.listSha(it.id) != null }.map { it.id }.toSet()
        }
        val pending = updatedAt != 0L && applied != enabledLists

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            HushCard {
                Text(
                    text = "DOMAINS LOADED",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (entryCount > 0) formatCount(entryCount) else "Starter list",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = summary(updatedAt, applied.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Row(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(onClick = onUpdateNow, enabled = !UpdateStatus.running) {
                        Text(if (UpdateStatus.running) "Updating" else "Update now")
                    }
                    UpdateStatus.message?.let { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                SettingRow(
                    title = "Update every week",
                    description = "Fetches fresh lists on Wi-Fi while charging.",
                    checked = weekly,
                    onChange = {
                        weekly = it
                        onWeeklyChange(it)
                    },
                    modifier = Modifier.padding(top = 16.dp),
                )
            }

            if (pending) {
                HushCard {
                    Text(
                        text = "List choices changed",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Update now to download the new selection.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
                    )
                    Button(onClick = onUpdateNow, enabled = !UpdateStatus.running) { Text("Update now") }
                }
            }

            for (category in ListCategory.entries) {
                SectionLabel(category.label)
                for (source in ListCatalog.inCategory(category)) {
                    ListRow(
                        source = source,
                        enabled = source.id in enabledLists,
                        count = remember(UpdateStatus.running) { prefs.listCount(source.id) },
                        onToggle = { on ->
                            enabledLists = if (on) enabledLists + source.id else enabledLists - source.id
                            prefs.setListEnabled(source.id, on)
                        },
                    )
                }
            }
        }
    }
}

private fun summary(updatedAt: Long, lists: Int): String {
    if (updatedAt == 0L) return "Only a small built-in list is in use. Tap Update now to download the full lists."
    val whenText = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(updatedAt))
    val noun = if (lists == 1) "list" else "lists"
    return "From $lists $noun. Updated $whenText."
}

@Composable
private fun ListRow(source: BlockListSource, enabled: Boolean, count: Int, onToggle: (Boolean) -> Unit) {
    HushCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    text = source.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = source.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (enabled && count > 0) {
                    Text(
                        text = "${formatCount(count)} domains",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
            Switch(checked = enabled, onCheckedChange = onToggle)
        }
    }
}

// --- Rules ---------------------------------------------------------------------------------

private fun androidx.compose.foundation.lazy.LazyListScope.rulesTab(prefs: Prefs) {
    item(key = "rules-body") {
        var custom by remember { mutableStateOf(prefs.customBlocked()) }
        var allowed by remember { mutableStateOf(prefs.allowed()) }
        var regexBlock by remember { mutableStateOf(prefs.regexBlock()) }
        var regexAllow by remember { mutableStateOf(prefs.regexAllow()) }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionLabel("Blocked sites · ${custom.size}")
            DomainEditor(
                description = "Always blocked, including every subdomain. Paste a domain or a link.",
                hint = "example.com",
                items = custom,
                validate = ::validateDomain,
                onAdd = { domain ->
                    custom = custom + domain
                    prefs.setCustomBlocked(domain, true)
                },
                onRemove = { domain ->
                    custom = custom - domain
                    prefs.setCustomBlocked(domain, false)
                },
            )

            SectionLabel("Allowed sites · ${allowed.size}")
            DomainEditor(
                description = "Never blocked, even if a list or a rule would block them. Also exempt from the protections.",
                hint = "example.com",
                items = allowed,
                validate = ::validateDomain,
                onAdd = { domain ->
                    allowed = allowed + domain
                    prefs.setAllowed(domain, true)
                },
                onRemove = { domain ->
                    allowed = allowed - domain
                    prefs.setAllowed(domain, false)
                },
            )

            SectionLabel("Advanced: block patterns · ${regexBlock.size}")
            DomainEditor(
                description = "Regular expressions matched anywhere in a name, ignoring case. Use ^ and \$ to match a whole name. Slow or risky patterns are refused.",
                hint = "^ads[0-9]*\\.",
                items = regexBlock,
                validate = { input -> validatePattern(input, regexBlock.size) },
                onAdd = { pattern ->
                    regexBlock = regexBlock + pattern
                    prefs.setRegexBlock(pattern, true)
                },
                onRemove = { pattern ->
                    regexBlock = regexBlock - pattern
                    prefs.setRegexBlock(pattern, false)
                },
                keyboardType = KeyboardType.Ascii,
                monospace = true,
            )

            SectionLabel("Advanced: allow patterns · ${regexAllow.size}")
            DomainEditor(
                description = "Names matching these stay reachable, overriding lists and block patterns.",
                hint = "\\.mycompany\\.com\$",
                items = regexAllow,
                validate = { input -> validatePattern(input, regexAllow.size) },
                onAdd = { pattern ->
                    regexAllow = regexAllow + pattern
                    prefs.setRegexAllow(pattern, true)
                },
                onRemove = { pattern ->
                    regexAllow = regexAllow - pattern
                    prefs.setRegexAllow(pattern, false)
                },
                keyboardType = KeyboardType.Ascii,
                monospace = true,
            )
        }
    }
}

private fun validateDomain(input: String): EditorResult {
    val domain = DomainInput.parse(input)
    return if (domain != null) EditorResult(domain) else EditorResult(null, "Enter a domain like example.com")
}

private fun validatePattern(input: String, existingCount: Int): EditorResult {
    val pattern = input.trim()
    if (existingCount >= SafeRegex.MAX_RULES) return EditorResult(null, "Too many patterns")
    val problem = SafeRegex.check(pattern)
    return if (problem == null) EditorResult(pattern) else EditorResult(null, problem)
}

// --- Social --------------------------------------------------------------------------------

private fun androidx.compose.foundation.lazy.LazyListScope.socialTab(prefs: Prefs) {
    val quietNow = prefs.isLocked()
    item(key = "social-note") {
        Text(
            text = if (quietNow) {
                "Quiet hours are running: these can be blocked more, not less, until they end."
            } else {
                "Block a whole service all day, or only during your quiet hours."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
    item(key = "social-body") {
        val modes = remember {
            mutableStateMapOf<String, SocialMode>().apply {
                SocialSites.all.forEach { put(it.id, prefs.socialMode(it.id)) }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SocialSites.all.forEach { site ->
                SocialRow(
                    site = site,
                    mode = modes[site.id] ?: SocialMode.Off,
                    onModeChange = { mode ->
                        // Refused while the quiet window runs if it would block less.
                        if (prefs.setSocialMode(site.id, mode)) modes[site.id] = mode
                    },
                )
            }
        }
    }
}

@Composable
private fun SocialRow(site: SocialSite, mode: SocialMode, onModeChange: (SocialMode) -> Unit) {
    val summary = when (mode) {
        SocialMode.Off -> "Not blocked"
        SocialMode.Always -> "Blocked all day"
        SocialMode.Night -> "Blocked during quiet hours"
    }
    HushCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = site.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = mode != SocialMode.Off,
                onCheckedChange = { on ->
                    onModeChange(if (on) SocialMode.Always else SocialMode.Off)
                },
            )
        }
        if (mode != SocialMode.Off) {
            Row(
                modifier = Modifier.padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = mode == SocialMode.Always,
                    onClick = { onModeChange(SocialMode.Always) },
                    label = { Text("Always") },
                )
                FilterChip(
                    selected = mode == SocialMode.Night,
                    onClick = { onModeChange(SocialMode.Night) },
                    label = { Text("Quiet hours only") },
                )
            }
        }
    }
}
