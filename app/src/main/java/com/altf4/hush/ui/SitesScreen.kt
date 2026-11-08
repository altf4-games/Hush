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
import androidx.compose.ui.unit.dp
import com.altf4.hush.data.Prefs
import com.altf4.hush.data.SocialMode
import com.altf4.hush.data.SocialSite
import com.altf4.hush.data.SocialSites
import com.altf4.hush.dns.BlockListSource
import com.altf4.hush.dns.ListCatalog

@Composable
fun SitesScreen(prefs: Prefs) {
    var enabledLists by remember { mutableStateOf(prefs.enabledLists()) }
    val modes = remember {
        mutableStateMapOf<String, SocialMode>().apply {
            SocialSites.all.forEach { put(it.id, prefs.socialMode(it.id)) }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenHeader("Sites", "Block ads and distracting sites in every app and browser.") }

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

        item { SectionLabel("Block lists") }
        items(ListCatalog.all, key = { it.id }) { source ->
            ListRow(
                source = source,
                enabled = source.id in enabledLists,
                onToggle = { on ->
                    enabledLists = if (on) enabledLists + source.id else enabledLists - source.id
                    prefs.setListEnabled(source.id, on)
                },
            )
        }

        item { SectionLabel("Updates") }
        item {
            HushCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Weekly update",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "Wi-Fi and charging only. Not available yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = false, onCheckedChange = null, enabled = false)
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
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

@Composable
private fun ListRow(source: BlockListSource, enabled: Boolean, onToggle: (Boolean) -> Unit) {
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
    }
}
