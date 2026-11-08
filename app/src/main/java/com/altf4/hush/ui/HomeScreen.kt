package com.altf4.hush.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.altf4.hush.block.BlockerState
import com.altf4.hush.data.Prefs
import com.altf4.hush.data.SetupChecklist
import com.altf4.hush.data.SetupItem
import com.altf4.hush.dns.ListCatalog
import com.altf4.hush.dns.Stats
import com.altf4.hush.dns.UpdateStatus
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** The dashboard: protection, what has been blocked, and shortcuts to everything else. */
@Composable
fun HomeScreen(
    prefs: Prefs,
    protectionOn: Boolean,
    onProtectionChange: (Boolean) -> Unit,
    onUpdateNow: () -> Unit,
    onOpenFilters: () -> Unit,
    onOpenNight: () -> Unit,
    onOpenApps: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val status = statusColors
    // Show the saved counts even while the tunnel is down.
    LaunchedEffect(Unit) { Stats.attach(prefs) }
    val stats = Stats.view
    val resumed = rememberResumeCount()

    val entryCount = remember(UpdateStatus.running) { prefs.listsEntryCount }
    val listCount = remember(UpdateStatus.running) { ListCatalog.all.count { prefs.listSha(it.id) != null } }
    val provider = remember { prefs.dnsProvider }
    val privateDns = remember(resumed) { readPrivateDns(context) }
    val nightOn = remember { prefs.nightEnabled }

    val weekLetters = remember {
        val today = LocalDate.now()
        (6 downTo 0).map { today.minusDays(it.toLong()).dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()) }
    }
    val todayRate = if (stats.todayTotal > 0) stats.todayBlocked * 100.0 / stats.todayTotal else 0.0
    val weekBlocked = stats.weekBlocked.sum()

    val checklist = SetupChecklist.items(
        protectionOn,
        BlockerState.running,
        remember { prefs.blockedApps().size },
        nightOn,
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = ScreenPadding, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(CardGap),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(start = 4.dp, top = 20.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Hush",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                )
                if (protectionOn) {
                    StatusPill("Protected", status.protected, status.protectedContainer)
                } else {
                    StatusPill("Paused", status.paused, status.pausedContainer)
                }
            }
        }

        // Hero: the protection control and what it is doing right now.
        item {
            HushCard(shape = MaterialTheme.shapes.extraLarge) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    // Kept modest so today's numbers are visible without scrolling.
                    ProtectionOrb(on = protectionOn, onClick = { onProtectionChange(!protectionOn) }, size = 128.dp)
                    AnimatedContent(
                        targetState = protectionOn,
                        transitionSpec = { fadeIn(HushMotion.standard()) togetherWith fadeOut(HushMotion.standard()) },
                        label = "state",
                    ) { on ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = if (on) "You're protected" else "Protection is paused",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = if (on) {
                                    "Filtering ${compactCount(entryCount.coerceAtLeast(0))} domains · encrypted via ${provider.label}"
                                } else {
                                    "Tap the shield to block ads and trackers in every app."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 6.dp, bottom = 4.dp),
                            )
                        }
                    }
                }
            }
        }

        if (protectionOn && privateDns.bypassesHush) {
            item {
                HushCard(color = status.warningContainer) {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(Icons.Filled.Warning, contentDescription = null, tint = status.warning)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text("Private DNS is bypassing Hush", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                            Text(
                                "A Private DNS provider is set in system settings, so lookups skip blocking. Set it to Off or Automatic.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
                            )
                            FilledTonalButton(onClick = { openNetworkSettings(context) }) { Text("Open settings") }
                        }
                    }
                }
            }
        }

        // Bento: today's numbers.
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(CardGap)) {
                StatTile(
                    value = compactCount(stats.todayBlocked),
                    caption = "Blocked today",
                    icon = HushIcons.Shield,
                    modifier = Modifier.weight(1f),
                    extra = { Sparkline(stats.weekBlocked) },
                )
                StatTile(
                    value = "%.0f%%".format(todayRate),
                    caption = "of ${compactCount(stats.todayTotal)} lookups",
                    icon = HushIcons.Chart,
                    tint = status.protected,
                    container = status.protectedContainer,
                    modifier = Modifier.weight(1f),
                    extra = { RateBar(todayRate.toFloat() / 100f) },
                )
            }
        }

        item {
            HushCard {
                Row(verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        Text("This week", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            "${formatCount(weekBlocked)} blocked · ${formatCount(stats.lifetimeBlocked)} all time",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
                WeekChart(blocked = stats.weekBlocked, total = stats.weekTotal, labels = weekLetters)
                Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Legend(MaterialTheme.colorScheme.primary, "Blocked")
                    Legend(MaterialTheme.colorScheme.surfaceContainerHighest, "Allowed")
                }
            }
        }

        // Shortcuts.
        item {
            ListGroup {
                GroupRow(
                    title = "Filters",
                    subtitle = if (entryCount > 0) "${formatCount(entryCount)} domains from $listCount lists" else "Starter list only — download the full lists",
                    leading = { IconBadge(HushIcons.Filter) },
                    trailing = {
                        TextButton(onClick = onUpdateNow, enabled = !UpdateStatus.running) {
                            Text(if (UpdateStatus.running) "Updating…" else "Update")
                        }
                    },
                    onClick = onOpenFilters,
                )
                GroupDivider()
                GroupRow(
                    title = "DNS server",
                    subtitle = "${provider.label} · DNS-over-TLS",
                    leading = { IconBadge(HushIcons.Dns) },
                    trailing = { Chevron() },
                    onClick = onOpenSettings,
                )
                GroupDivider()
                GroupRow(
                    title = "Night lock",
                    subtitle = if (nightOn) "On · quiet hours scheduled" else "Off",
                    leading = { IconBadge(HushIcons.Moon) },
                    trailing = { Chevron() },
                    onClick = onOpenNight,
                )
            }
        }

        UpdateStatus.message?.let { message ->
            item {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        }

        if (!SetupChecklist.isComplete(checklist)) {
            item {
                SetupCard(
                    items = checklist,
                    actions = mapOf(
                        SetupItem.Id.Protection to ("Turn on" to { onProtectionChange(true) }),
                        SetupItem.Id.Accessibility to ("Open" to { openAccessibilitySettings(context) }),
                        SetupItem.Id.Apps to ("Choose" to onOpenApps),
                        SetupItem.Id.Night to ("Set up" to onOpenNight),
                    ),
                )
            }
        }

        item {
            Text(
                text = "Only counts are kept. The names of sites you visit are never stored.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun RateBar(share: Float) {
    val status = statusColors
    // Same height as the sparkline in the neighbouring tile, so the tiles line up.
    Box(Modifier.fillMaxWidth().height(28.dp), contentAlignment = Alignment.BottomStart) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(share.coerceIn(0f, 1f))
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(status.protected),
            )
        }
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun Chevron() {
    Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
}
