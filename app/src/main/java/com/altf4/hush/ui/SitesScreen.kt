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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
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

@Composable
fun SitesScreen(
    prefs: Prefs,
    protectionOn: Boolean,
    onProtectionChange: (Boolean) -> Unit,
    onUpdateNow: () -> Unit,
    onWeeklyChange: (Boolean) -> Unit,
) {
    var enabledLists by remember { mutableStateOf(prefs.enabledLists()) }
    var custom by remember { mutableStateOf(prefs.customBlocked()) }
    var allowed by remember { mutableStateOf(prefs.allowed()) }
    var weekly by remember { mutableStateOf(prefs.weeklyUpdate) }
    var regexBlock by remember { mutableStateOf(prefs.regexBlock()) }
    var regexAllow by remember { mutableStateOf(prefs.regexAllow()) }
    val modes = remember {
        mutableStateMapOf<String, SocialMode>().apply {
            SocialSites.all.forEach { put(it.id, prefs.socialMode(it.id)) }
        }
    }

    // Re-read the update details whenever an update starts or finishes.
    val updatedAt = remember(UpdateStatus.running) { prefs.listsUpdatedAt }
    val entryCount = remember(UpdateStatus.running) { prefs.listsEntryCount }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenHeader("Sites", "Block ads and distracting sites in every app and browser.") }

        item {
            HushCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = "Site blocking",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = if (protectionOn) "On" else "Off. Turn on to apply the choices below.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = protectionOn, onCheckedChange = onProtectionChange)
                }
                Text(
                    text = "Turn off Secure DNS in your browser and Private DNS in system settings, or they bypass this.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }

        item { SectionLabel("Protection") }
        item { ProtectionCard(prefs) }

        item { SectionLabel("Social sites") }
        items(SocialSites.all, key = { it.id }) { site ->
            SocialRow(
                site = site,
                mode = modes[site.id] ?: SocialMode.Off,
                onModeChange = { mode ->
                    modes[site.id] = mode
                    prefs.setSocialMode(site.id, mode)
                },
            )
        }

        item { SectionLabel("Your blocked sites") }
        item {
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
        }

        item { SectionLabel("Always allowed") }
        item {
            DomainEditor(
                description = "Never blocked, even if a list or a rule above would block them. Also exempt from the protections.",
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
        }

        item { SectionLabel("Block patterns") }
        item {
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
        }

        item { SectionLabel("Allow patterns") }
        item {
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

        for (category in ListCategory.entries) {
            item(key = "label-${category.name}") { SectionLabel("Lists: ${category.label}") }
            items(ListCatalog.inCategory(category), key = { it.id }) { source ->
                val sha = remember(UpdateStatus.running) { prefs.listSha(source.id) }
                ListRow(
                    source = source,
                    enabled = source.id in enabledLists,
                    sha = sha,
                    onToggle = { on ->
                        enabledLists = if (on) enabledLists + source.id else enabledLists - source.id
                        prefs.setListEnabled(source.id, on)
                    },
                )
            }
        }

        item {
            HushCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = "Update lists",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = updateSummary(updatedAt, entryCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Button(onClick = onUpdateNow, enabled = !UpdateStatus.running) {
                        Text(if (UpdateStatus.running) "Updating" else "Update now")
                    }
                }
                UpdateStatus.message?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                Text(
                    text = "Changes to the list choices apply the next time the lists are updated.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Row(
                    modifier = Modifier.padding(top = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = "Weekly update",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "Runs on Wi-Fi while charging.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = weekly,
                        onCheckedChange = { on ->
                            weekly = on
                            onWeeklyChange(on)
                        },
                    )
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
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

private fun updateSummary(updatedAt: Long, entryCount: Int): String {
    if (updatedAt == 0L) return "Using the built-in starter list"
    val whenText = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
        .format(Date(updatedAt))
    return "Updated $whenText · ${"%,d".format(entryCount)} domains"
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

@Composable
private fun ListRow(
    source: BlockListSource,
    enabled: Boolean,
    sha: String?,
    onToggle: (Boolean) -> Unit,
) {
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
            }
            Switch(checked = enabled, onCheckedChange = onToggle)
        }
        if (enabled && sha != null) {
            Text(
                text = "SHA-256 ${sha.take(8)}…${sha.takeLast(6)}",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
