package com.altf4.hush.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.altf4.hush.block.AppEntry
import com.altf4.hush.block.InstalledApps
import com.altf4.hush.data.Prefs

@Composable
fun AppsScreen(prefs: Prefs) {
    val appContext = LocalContext.current.applicationContext
    var blocked by remember { mutableStateOf(prefs.blockedApps()) }
    var apps by remember { mutableStateOf<List<AppEntry>?>(null) }
    var query by remember { mutableStateOf("") }

    // Icons and labels take a moment; load them off the main thread, once.
    LaunchedEffect(Unit) {
        Thread({ apps = InstalledApps.load(appContext) }, "hush-apps").start()
    }

    val all = apps
    val shown = remember(all, query) {
        all?.filter { it.label.contains(query.trim(), ignoreCase = true) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { ScreenHeader("Apps", "Choose which apps are closed during quiet hours.") }

        item {
            HushCard {
                Text(
                    text = if (blocked.isEmpty()) "No apps chosen" else "${blocked.size} chosen",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Select all skips apps you usually need, like messages, maps and the clock. You can still tick them yourself.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        enabled = all != null,
                        onClick = {
                            val chosen = all.orEmpty().filter { !it.essential }.map { it.packageName }
                            blocked = blocked + chosen
                            prefs.setBlockedApps(blocked)
                        },
                    ) { Text("Select all") }
                    OutlinedButton(
                        enabled = blocked.isNotEmpty(),
                        onClick = {
                            blocked = emptySet()
                            prefs.setBlockedApps(blocked)
                        },
                    ) { Text("Clear") }
                }
            }
        }

        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Search apps") },
                shape = MaterialTheme.shapes.medium,
            )
        }

        if (shown == null) {
            item {
                Text(
                    text = "Loading apps…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        } else {
            items(shown, key = { it.packageName }) { app ->
                AppRow(
                    app = app,
                    checked = app.packageName in blocked,
                    onChange = { on ->
                        blocked = if (on) blocked + app.packageName else blocked - app.packageName
                        prefs.setAppBlocked(app.packageName, on)
                    },
                )
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun AppRow(app: AppEntry, checked: Boolean, onChange: (Boolean) -> Unit) {
    HushCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val icon = app.icon
            if (icon != null) {
                Image(
                    bitmap = icon,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)),
                )
            } else {
                Spacer(Modifier.size(40.dp))
            }
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                if (app.essential) {
                    Text(
                        text = "Usually needed",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
}
