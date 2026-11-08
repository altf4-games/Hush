package com.altf4.hush.ui

import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.alpha
import com.altf4.hush.block.BlockerState
import com.altf4.hush.block.EmergencyPass
import com.altf4.hush.data.Prefs
import com.altf4.hush.data.Schedule
import com.altf4.hush.data.SetupChecklist
import com.altf4.hush.data.SetupItem
import com.altf4.hush.data.ThemeMode
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

private val DAY_LETTERS = listOf("M", "T", "W", "T", "F", "S", "S")

private fun formatMinutes(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)

private fun describe(schedule: Schedule, now: ZonedDateTime): String {
    if (!schedule.enabled) return "Off"
    if ((schedule.days and Schedule.ALL_DAYS) == 0) return "Pick at least one day"
    if (schedule.startMinute == schedule.endMinute) return "Start and end must be different"
    val next = schedule.nextChange(now) ?: return "Off"
    val pattern = if (next.toLocalDate() == now.toLocalDate()) "HH:mm" else "EEE HH:mm"
    val at = next.format(DateTimeFormatter.ofPattern(pattern))
    return if (schedule.isActive(now)) "Active now, ends $at" else "Next window starts $at"
}

private fun pickTime(context: Context, minutes: Int, onPicked: (Int) -> Unit) {
    TimePickerDialog(
        context,
        { _, hour, minute -> onPicked(hour * 60 + minute) },
        minutes / 60,
        minutes % 60,
        true,
    ).show()
}

@Composable
fun NightScreen(
    prefs: Prefs,
    themeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    protectionOn: Boolean,
    onProtectionChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(prefs.nightEnabled) }
    var start by remember { mutableIntStateOf(prefs.nightStartMinutes) }
    var end by remember { mutableIntStateOf(prefs.nightEndMinutes) }
    var days by remember { mutableIntStateOf(prefs.nightDays) }
    var strict by remember { mutableStateOf(prefs.strictMode) }
    val appCount = remember { prefs.blockedApps().size }

    val schedule = Schedule(enabled, start, end, days)
    val status = remember(enabled, start, end, days) { describe(schedule, ZonedDateTime.now()) }
    // While the window runs, settings can only get stricter (see SettingsLock). Prefs enforces it too.
    val locked = remember(enabled, start, end, days, strict) { prefs.isLocked() }

    val checklist = SetupChecklist.items(protectionOn, BlockerState.running, appCount, enabled)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenHeader("Night lock", "Quiet hours for the apps you choose.") }

        if (!SetupChecklist.isComplete(checklist)) {
            item {
                SetupCard(
                    items = checklist,
                    actions = mapOf(
                        SetupItem.Id.Protection to ("Turn on" to { onProtectionChange(true) }),
                        SetupItem.Id.Accessibility to (
                            "Open" to { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                            ),
                    ),
                )
            }
        }

        if (locked) {
            item {
                HushCard {
                    Text(
                        text = "Locked until the window ends",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "The schedule is frozen and apps cannot be removed. You can still add apps or turn on strict mode.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }

        item {
            HushCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = "QUIET WINDOW",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "${formatMinutes(start)} – ${formatMinutes(end)}",
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = status,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = enabled,
                        enabled = !locked,
                        onCheckedChange = { if (prefs.setNightEnabled(it)) enabled = it },
                    )
                }
            }
        }

        item {
            HushCard {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    TimeColumn("Starts", start, !locked, Modifier.weight(1f)) {
                        pickTime(context, start) { picked ->
                            if (prefs.setNightWindow(picked, end)) start = picked
                        }
                    }
                    TimeColumn("Ends", end, !locked, Modifier.weight(1f)) {
                        pickTime(context, end) { picked ->
                            if (prefs.setNightWindow(start, picked)) end = picked
                        }
                    }
                }
                Text(
                    text = "If it ends earlier than it starts, it runs past midnight.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Text(
                    text = "WINDOW STARTS ON",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DAY_LETTERS.forEachIndexed { index, letter ->
                        val selected = (days and (1 shl index)) != 0
                        DayToggle(letter, selected, !locked) {
                            val next = days xor (1 shl index)
                            if (prefs.setNightDays(next)) days = next
                        }
                    }
                }
            }
        }

        item { SectionLabel("App closing") }

        item {
            HushCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = "Accessibility service",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = if (BlockerState.running) "On" else "Off. Turn on Hush in Accessibility settings.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Button(onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) {
                        Text("Open")
                    }
                }
                Text(
                    text = "It only notices which app opened and cannot read the screen. If the switch is greyed out, open Hush's app info, tap the menu and choose Allow restricted settings.",
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
                            text = "Strict mode",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "No emergency pass. Can only be turned off outside quiet hours.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = strict,
                        enabled = !(locked && strict),
                        onCheckedChange = { on ->
                            if (prefs.setStrictMode(on)) {
                                strict = on
                                if (on) EmergencyPass.shared.clear()
                            }
                        },
                    )
                }
            }
        }

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
    }
}

@Composable
private fun TimeColumn(label: String, minutes: Int, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(8.dp))
        FilledTonalButton(onClick = onClick, enabled = enabled) {
            Text(formatMinutes(minutes), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun DayToggle(letter: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val background = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh
    val content = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .size(36.dp)
            .alpha(if (enabled) 1f else 0.5f)
            .clip(CircleShape)
            .background(background)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = letter, style = MaterialTheme.typography.labelLarge, color = content)
    }
}
