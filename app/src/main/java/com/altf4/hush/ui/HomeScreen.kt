package com.altf4.hush.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.altf4.hush.block.BlockerState
import com.altf4.hush.data.Prefs
import com.altf4.hush.data.SetupChecklist
import com.altf4.hush.data.SetupItem
import com.altf4.hush.dns.Stats
import com.altf4.hush.dns.UpdateStatus
import java.text.DateFormat
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Date
import java.util.Locale

/** The dashboard: protection switch, what has been blocked, and the state of the filters. */
@Composable
fun HomeScreen(
    prefs: Prefs,
    protectionOn: Boolean,
    onProtectionChange: (Boolean) -> Unit,
    onUpdateNow: () -> Unit,
    onOpenFilters: () -> Unit,
    onOpenApps: () -> Unit,
    onOpenNight: () -> Unit,
) {
    val context = LocalContext.current
    // Show the saved counts even while the tunnel is down.
    LaunchedEffect(Unit) { Stats.attach(prefs) }
    val stats = Stats.view

    val updatedAt = remember(UpdateStatus.running) { prefs.listsUpdatedAt }
    val entryCount = remember(UpdateStatus.running) { prefs.listsEntryCount }
    val listCount = remember(UpdateStatus.running) {
        com.altf4.hush.dns.ListCatalog.all.count { prefs.listSha(it.id) != null }
    }

    val weekLetters = remember {
        val today = LocalDate.now()
        (6 downTo 0).map { today.minusDays(it.toLong()).dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()) }
    }
    val weekBlocked = stats.weekBlocked.sum()
    val rate = if (stats.lifetimeTotal > 0) (stats.lifetimeBlocked * 100.0 / stats.lifetimeTotal) else 0.0

    val checklist = SetupChecklist.items(
        protectionOn,
        BlockerState.running,
        remember { prefs.blockedApps().size },
        remember { prefs.nightEnabled },
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenHeader("Hush", "Ads, trackers and distractions, blocked on this phone.") }

        item {
            HushCard {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(8.dp))
                    PowerButton(on = protectionOn, onClick = { onProtectionChange(!protectionOn) })
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = if (protectionOn) "Protection is on" else "Protection is off",
                        style = MaterialTheme.typography.titleLarge,
                        color = if (protectionOn) successColor() else MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = if (protectionOn) {
                            "Lookups are filtered and sent encrypted to Quad9."
                        } else {
                            "Tap the button to start blocking in every app and browser."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                    )
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(
                    value = formatCount(stats.todayBlocked),
                    caption = "Blocked today",
                    modifier = Modifier.weight(1f),
                    accent = MaterialTheme.colorScheme.primary,
                )
                StatTile(
                    value = formatCount(stats.todayTotal),
                    caption = "Queries today",
                    modifier = Modifier.weight(1f),
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(
                    value = "%.1f%%".format(rate),
                    caption = "Blocked overall",
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    value = if (entryCount > 0) formatCount(entryCount) else "Starter",
                    caption = if (entryCount > 0) "Domains in filters" else "Small built-in list",
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item {
            HushCard {
                Row(verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "LAST 7 DAYS",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "${formatCount(weekBlocked)} blocked",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Text(
                        text = "${formatCount(stats.lifetimeBlocked)} since the start",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(16.dp))
                BarChart(values = stats.weekBlocked, labels = weekLetters)
                Text(
                    text = "Only counts are kept. Names of the sites you visit are never stored.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }

        item {
            HushCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = "Filters",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = filterSummary(updatedAt, entryCount, listCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Button(onClick = onUpdateNow, enabled = !UpdateStatus.running) {
                        Text(
                            when {
                                UpdateStatus.running -> "Updating"
                                updatedAt == 0L -> "Download"
                                else -> "Update"
                            },
                        )
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
                androidx.compose.material3.TextButton(onClick = onOpenFilters) { Text("Choose filters") }
            }
        }

        if (!SetupChecklist.isComplete(checklist)) {
            item {
                SetupCard(
                    items = checklist,
                    actions = mapOf(
                        SetupItem.Id.Protection to ("Turn on" to { onProtectionChange(true) }),
                        SetupItem.Id.Accessibility to (
                            "Open" to { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                            ),
                        SetupItem.Id.Apps to ("Choose" to onOpenApps),
                        SetupItem.Id.Night to ("Set up" to onOpenNight),
                    ),
                )
            }
        }

        item { Spacer(Modifier.height(8.dp)) }
    }
}

private fun filterSummary(updatedAt: Long, entryCount: Int, lists: Int): String {
    if (updatedAt == 0L) return "Only a small starter list is in use. Download the full lists to block much more."
    val whenText = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(updatedAt))
    val noun = if (lists == 1) "list" else "lists"
    return "${formatCount(entryCount)} domains from $lists $noun. Updated $whenText."
}
