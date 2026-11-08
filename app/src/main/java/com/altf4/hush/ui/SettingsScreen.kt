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
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.altf4.hush.data.Prefs
import com.altf4.hush.data.ThemeMode
import com.altf4.hush.dns.ListCatalog
import com.altf4.hush.dns.Stats

/** Protection options, appearance, backup, statistics and the About section. */
@Composable
fun SettingsScreen(
    prefs: Prefs,
    themeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    onRestored: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenHeader("Settings", "Protection, appearance and backup.") }

        item { SectionLabel("Protection") }
        item { ProtectionCard(prefs) }

        item { SectionLabel("Appearance") }
        item {
            HushCard {
                Text(
                    text = "Theme",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = themeMode == mode,
                            onClick = { onThemeChange(mode) },
                            label = { Text(mode.label) },
                        )
                    }
                }
            }
        }

        item { SectionLabel("Backup") }
        item { BackupCard(prefs, onRestored) }

        item { SectionLabel("Statistics") }
        item { StatisticsCard() }

        item { SectionLabel("About") }
        item { AboutCard() }

        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun StatisticsCard() {
    var confirming by remember { mutableStateOf(false) }
    val stats = Stats.view
    HushCard {
        Text(
            text = "${formatCount(stats.lifetimeBlocked)} blocked of ${formatCount(stats.lifetimeTotal)} lookups",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "Only these counts are kept, to draw the charts on Home. Reset them any time.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )
        FilledTonalButton(
            onClick = {
                if (confirming) {
                    Stats.reset()
                    confirming = false
                } else {
                    confirming = true
                }
            },
        ) { Text(if (confirming) "Tap again to reset" else "Reset statistics") }
    }
}

@Composable
private fun AboutCard() {
    val context = LocalContext.current
    val version = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
        } catch (e: Exception) {
            ""
        }
    }
    HushCard {
        Text(
            text = "Hush",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (version.isNotEmpty()) {
            Text(
                text = "Version $version",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = "Blocks ads, trackers and sites you choose on your whole phone, and closes distracting apps during quiet hours. It all runs on this device.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 12.dp),
        )

        AboutSection(
            "How it works",
            "Hush starts a local VPN that carries only DNS lookups; all other traffic goes straight out as usual. " +
                "A lookup for a blocked name gets an empty answer. Everything else is sent to Quad9 over an encrypted " +
                "connection, with the certificate checked, and never in the clear. Night lock uses an accessibility " +
                "service that only learns which app was opened.",
        )
        AboutSection(
            "Privacy",
            "No account, no ads, no analytics. The names you look up are never saved or logged. The only things " +
                "stored are your settings and a few counters. The app talks to Quad9 (9.9.9.9 and 149.112.112.112, " +
                "port 853) and to the list hosts you switch on.",
        )
        AboutSection(
            "Filter lists",
            "${ListCatalog.all.size} lists to choose from: " + ListCatalog.all.joinToString(", ") { it.name } + ". " +
                "They are downloaded over HTTPS from fixed addresses built into the app.",
        )
        AboutSection(
            "Good to know",
            "Blocking works by name, so ads served from the same address as the content cannot be removed, and " +
                "apps that use their own DNS skip it. Turn off Private DNS in system settings and Secure DNS in " +
                "your browser, or they bypass Hush.",
        )
    }
}

@Composable
private fun AboutSection(title: String, body: String) {
    Column(Modifier.padding(top = 16.dp)) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
