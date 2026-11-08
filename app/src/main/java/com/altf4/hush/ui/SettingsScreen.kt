package com.altf4.hush.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.altf4.hush.data.Prefs
import com.altf4.hush.data.ThemeMode
import com.altf4.hush.dns.DnsProvider
import com.altf4.hush.dns.Stats

/** DNS server, protection options, appearance, data, and the About page. */
@Composable
fun SettingsScreen(
    prefs: Prefs,
    themeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    dynamicColor: Boolean,
    onDynamicColorChange: (Boolean) -> Unit,
    onRestored: () -> Unit,
    onReplaySetup: () -> Unit,
) {
    var showAbout by rememberSaveable { mutableStateOf(false) }
    if (showAbout) {
        BackHandler { showAbout = false }
        AboutScreen(onBack = { showAbout = false })
        return
    }

    var provider by remember { mutableStateOf(prefs.dnsProvider) }
    var uncloak by remember { mutableStateOf(prefs.uncloakCname) }
    var rebinding by remember { mutableStateOf(prefs.blockRebinding) }
    var bypass by remember { mutableStateOf(prefs.blockBypass) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = ScreenPadding, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(CardGap),
    ) {
        item { ScreenHeader("Settings") }

        item { SectionLabel("DNS server") }
        item {
            ListGroup {
                DnsProvider.entries.forEachIndexed { index, p ->
                    if (index > 0) GroupDivider()
                    RadioRow(
                        title = if (p == DnsProvider.DEFAULT) "${p.label} (default)" else p.label,
                        subtitle = p.description,
                        selected = provider == p,
                        onSelect = {
                            provider = p
                            prefs.dnsProvider = p
                        },
                    )
                }
            }
        }
        item {
            Footnote(
                "Lookups that are not blocked go to this server over DNS-over-TLS, with its certificate checked. " +
                    "If it cannot be reached, lookups fail rather than go out unencrypted.",
            )
        }

        item { SectionLabel("Protection") }
        item {
            ListGroup {
                SwitchRow(
                    title = "Block disguised trackers",
                    subtitle = "Catches trackers hidden behind another name (CNAME cloaking).",
                    checked = uncloak,
                    onChange = {
                        uncloak = it
                        prefs.uncloakCname = it
                    },
                )
                GroupDivider()
                SwitchRow(
                    title = "Block DNS rebinding",
                    subtitle = "Stops public sites from pointing at devices on your network.",
                    checked = rebinding,
                    onChange = {
                        rebinding = it
                        prefs.blockRebinding = it
                    },
                )
                GroupDivider()
                SwitchRow(
                    title = "Block secure-DNS bypass",
                    subtitle = "Stops browsers switching to their own encrypted DNS. May break a custom browser setup.",
                    checked = bypass,
                    onChange = {
                        bypass = it
                        prefs.blockBypass = it
                    },
                )
            }
        }

        item { SectionLabel("Appearance") }
        item {
            HushCard {
                Text("Theme", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                SegmentTabs(
                    labels = ThemeMode.entries.map { it.label },
                    selected = ThemeMode.entries.indexOf(themeMode),
                    onSelect = { onThemeChange(ThemeMode.entries[it]) },
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
        if (dynamicColorSupported) {
            item {
                ListGroup {
                    SwitchRow(
                        title = "Wallpaper colours",
                        subtitle = "Take the accent colour from your wallpaper.",
                        checked = dynamicColor,
                        onChange = onDynamicColorChange,
                    )
                }
            }
        }

        item { SectionLabel("Your data") }
        item { BackupCard(prefs, onRestored) }
        item { StatisticsGroup() }

        item { SectionLabel("Help") }
        item {
            ListGroup {
                GroupRow(
                    title = "Run setup again",
                    subtitle = "Walk through the permissions and checks.",
                    leading = { IconBadge(Icons.Filled.Refresh) },
                    trailing = { Chevron() },
                    onClick = onReplaySetup,
                )
                GroupDivider()
                GroupRow(
                    title = "About Hush",
                    subtitle = "How it works, privacy, and filter sources.",
                    leading = { IconBadge(Icons.Filled.Info) },
                    trailing = { Chevron() },
                    onClick = { showAbout = true },
                )
            }
        }
        item { Footnote("") }
    }
}

@Composable
private fun StatisticsGroup() {
    var confirming by remember { mutableStateOf(false) }
    val stats = Stats.view
    ListGroup {
        GroupRow(
            title = "Statistics",
            subtitle = "${formatCount(stats.lifetimeBlocked)} blocked of ${formatCount(stats.lifetimeTotal)} lookups. Only counts are kept.",
            leading = { IconBadge(HushIcons.Chart) },
            trailing = {
                TextButton(
                    onClick = {
                        if (confirming) {
                            Stats.reset()
                            confirming = false
                        } else {
                            confirming = true
                        }
                    },
                ) { Text(if (confirming) "Confirm" else "Reset") }
            },
        )
    }
}

@Composable
fun Footnote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 8.dp),
    )
}
