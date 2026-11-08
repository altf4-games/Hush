package com.altf4.hush.ui

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.altf4.hush.block.BlockerState
import com.altf4.hush.data.Onboarding
import com.altf4.hush.data.Onboarding.Step
import com.altf4.hush.data.Prefs
import com.altf4.hush.dns.UpdateStatus
import com.altf4.hush.data.PrivateDnsMode
import com.altf4.hush.dns.DnsProvider

/**
 * First-run setup. One idea per page, and each permission is explained before the system asks.
 * Every check is re-read when the person comes back from system settings. Everything but site
 * blocking can be skipped, and the Home checklist keeps track of what is left.
 */
@Composable
fun OnboardingScreen(
    prefs: Prefs,
    protectionOn: Boolean,
    onProtectionChange: (Boolean) -> Unit,
    onFinish: () -> Unit,
) {
    val context = LocalContext.current
    var index by rememberSaveable { mutableStateOf(0) }
    val step = Onboarding.steps[index]
    val resumed = rememberResumeCount()
    val privateDns = remember(resumed) { readPrivateDns(context) }
    val battery = remember(resumed) { isBatteryUnrestricted(context) }
    val accessibilityOn = BlockerState.running
    var provider by remember { mutableStateOf(prefs.dnsProvider) }
    var openedAccessibility by rememberSaveable { mutableStateOf(false) }

    fun next() {
        if (index < Onboarding.steps.lastIndex) index++ else onFinish()
    }

    BackHandler(enabled = index > 0) { index-- }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            StepBars(count = Onboarding.steps.size, current = index, modifier = Modifier.weight(1f))
            Box(Modifier.heightIn(min = 48.dp), contentAlignment = Alignment.CenterEnd) {
                if (step != Step.Done) {
                    TextButton(onClick = onFinish) { Text("Skip") }
                }
            }
        }

        AnimatedContent(
            targetState = index,
            transitionSpec = {
                val forward = targetState > initialState
                (slideInHorizontally(HushMotion.standard()) { if (forward) it / 4 else -it / 4 } + fadeIn(HushMotion.standard()))
                    .togetherWith(slideOutHorizontally(HushMotion.standard()) { if (forward) -it / 4 else it / 4 } + fadeOut(HushMotion.standard()))
            },
            modifier = Modifier.weight(1f),
            label = "step",
        ) { shown ->
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(top = 32.dp, bottom = 16.dp),
            ) {
                when (Onboarding.steps[shown]) {
                    Step.Welcome -> WelcomePage()
                    Step.Protection -> ProtectionPage(protectionOn)
                    Step.Dns -> DnsPage(provider) {
                        provider = it
                        prefs.dnsProvider = it
                    }
                    Step.PrivateDns -> PrivateDnsPage(privateDns, onOpen = { openNetworkSettings(context) })
                    Step.NightLock -> NightLockPage(accessibilityOn, openedAccessibility, onOpenAppInfo = { openAppInfo(context) })
                    Step.Battery -> BatteryPage(battery)
                    Step.Done -> DonePage(prefs, protectionOn, provider, privateDns, accessibilityOn)
                }
            }
        }

        // Actions stay in the same place on every page.
        Column(Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (step) {
                Step.Welcome -> Primary("Get started") { next() }
                Step.Protection -> if (protectionOn) {
                    Primary("Continue") { next() }
                } else {
                    Primary("Turn on site blocking") { onProtectionChange(true) }
                    Secondary("Not now") { next() }
                }
                Step.Dns -> Primary("Continue") { next() }
                Step.PrivateDns -> Primary(if (privateDns.bypassesHush) "I'll do it later" else "Continue") { next() }
                Step.NightLock -> if (accessibilityOn) {
                    Primary("Continue") { next() }
                } else {
                    Primary("Agree and open settings") {
                        openedAccessibility = true
                        openAccessibilitySettings(context)
                    }
                    Secondary("Not now") { next() }
                }
                Step.Battery -> if (battery) {
                    Primary("Continue") { next() }
                } else {
                    Primary("Open battery settings") { openBatterySettings(context) }
                    Secondary("Skip") { next() }
                }
                Step.Done -> Primary("Go to Hush") { onFinish() }
            }
        }
    }
}

@Composable
private fun Primary(text: String, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth().height(56.dp)) {
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun Secondary(text: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth().height(56.dp)) {
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun PageHeader(icon: ImageVector, title: String, body: String, optional: Boolean = false) {
    IconBadge(icon, tint = MaterialTheme.colorScheme.primary, container = MaterialTheme.colorScheme.primaryContainer, size = 64)
    Spacer(Modifier.height(24.dp))
    if (optional) {
        Text("Optional", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(4.dp))
    }
    Text(title, style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onSurface)
    Spacer(Modifier.height(12.dp))
    Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(24.dp))
}

/** A check line: green tick when fine, amber warning when it needs attention. Never colour alone. */
@Composable
private fun CheckLine(ok: Boolean, title: String, detail: String) {
    val status = statusColors
    HushCard(color = if (ok) status.protectedContainer else status.warningContainer) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                if (ok) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                contentDescription = if (ok) "Done" else "Needs attention",
                tint = if (ok) status.protected else status.warning,
            )
            Column(Modifier.padding(start = 12.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun WelcomePage() {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        ProtectionOrb(on = true, onClick = {}, size = 148.dp)
    }
    Spacer(Modifier.height(8.dp))
    Text("A quieter phone", style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.onSurface)
    Spacer(Modifier.height(12.dp))
    Text(
        "Hush blocks ads, trackers and distracting sites in every app, and can close chosen apps at night. Setup takes about a minute.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(24.dp))
    ListGroup {
        GroupRow("Blocks ads and trackers", "In apps and browsers, using trusted lists.", leading = { IconBadge(HushIcons.Filter, size = 36) })
        GroupDivider()
        GroupRow("Keeps lookups private", "Encrypted DNS, and no browsing history kept.", leading = { IconBadge(HushIcons.Dns, size = 36) })
        GroupDivider()
        GroupRow("Quiet hours", "Closes the apps you pick at night.", leading = { IconBadge(HushIcons.Moon, size = 36) })
    }
}

@Composable
private fun ProtectionPage(on: Boolean) {
    PageHeader(
        HushIcons.Shield,
        "Turn on site blocking",
        "Android will ask you to allow a VPN connection. Hush uses it only to see the DNS lookups on this phone, so it can block ads and trackers. Your other traffic is not routed through Hush, and nothing is sent to us.",
    )
    if (on) {
        CheckLine(true, "Site blocking is on", "You'll see a key icon in the status bar while it runs.")
    }
}

@Composable
private fun DnsPage(provider: DnsProvider, onSelect: (DnsProvider) -> Unit) {
    PageHeader(
        HushIcons.Dns,
        "Pick a DNS server",
        "Lookups that aren't blocked are sent here, encrypted. Google is a good default; you can change it any time in Settings.",
    )
    ListGroup {
        DnsProvider.entries.forEachIndexed { index, p ->
            if (index > 0) GroupDivider()
            RadioRow(
                title = if (p == DnsProvider.DEFAULT) "${p.label} (recommended)" else p.label,
                subtitle = p.description,
                selected = provider == p,
                onSelect = { onSelect(p) },
            )
        }
    }
}

@Composable
private fun PrivateDnsPage(mode: PrivateDnsMode, onOpen: () -> Unit) {
    PageHeader(
        HushIcons.Bolt,
        "Check for bypasses",
        "Two settings can send lookups around Hush. Here's how to make sure they don't.",
    )
    when (mode) {
        PrivateDnsMode.Strict -> {
            CheckLine(false, "Private DNS is set to a provider", "Open Network settings → Private DNS and choose Off or Automatic.")
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onOpen) { Text("Open network settings") }
        }
        PrivateDnsMode.Unknown -> CheckLine(true, "Private DNS", "Couldn't read it. If you set a provider under Private DNS, choose Off or Automatic.")
        else -> CheckLine(true, "Private DNS is fine", if (mode == PrivateDnsMode.Off) "It's off." else "It's on Automatic, which works with Hush.")
    }
    Spacer(Modifier.height(CardGap))
    HushCard {
        Text("Secure DNS in your browser", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Text(
            "Browsers can use their own encrypted DNS and skip Hush. In Chrome, open Settings → Privacy and security → Use secure DNS and turn it off. Other browsers have a similar setting.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun NightLockPage(on: Boolean, opened: Boolean, onOpenAppInfo: () -> Unit) {
    PageHeader(
        HushIcons.Moon,
        "Night lock",
        "Close the apps you choose during quiet hours. This uses an Android accessibility service, so please read what it does before you agree.",
        optional = true,
    )
    if (on) {
        CheckLine(true, "Accessibility service is on", "Pick apps and a schedule in the Night tab.")
        return
    }
    // Prominent disclosure, shown in the app before the person turns the service on.
    ListGroup {
        GroupRow("What it sees", "Only which app has just opened. It cannot read the screen, what you type, or passwords.", leading = { IconBadge(HushIcons.Accessibility, size = 36) })
        GroupDivider()
        GroupRow("What it does", "If that app is one you chose and quiet hours are running, it closes it.", leading = { IconBadge(HushIcons.Moon, size = 36) })
        GroupDivider()
        GroupRow("What it keeps", "Nothing. No app history is stored or sent anywhere.", leading = { IconBadge(HushIcons.ShieldCheck, size = 36) })
    }
    if (opened && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Spacer(Modifier.height(CardGap))
        HushCard {
            Text("Switch greyed out?", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(
                "For apps installed outside the Play Store, Android hides this setting. Tap the greyed-out switch once, then open Hush's App info, tap ⋮ and choose Allow restricted settings. Come back and turn it on.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )
            OutlinedButton(onClick = onOpenAppInfo) { Text("Open App info") }
        }
    }
}

@Composable
private fun BatteryPage(unrestricted: Boolean) {
    PageHeader(
        HushIcons.Battery,
        "Keep Hush running",
        "Some phones stop background apps to save battery, which can switch blocking off or stop it coming back after a restart.",
        optional = true,
    )
    if (unrestricted) {
        CheckLine(true, "Battery optimisation is off for Hush", "Blocking can keep running and restart after a reboot.")
    } else {
        HushCard {
            Text("How to do it", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(
                "In the list that opens, switch to All apps, find Hush and choose Don't optimise (or Unrestricted).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun DonePage(prefs: Prefs, protectionOn: Boolean, provider: DnsProvider, privateDns: PrivateDnsMode, accessibilityOn: Boolean) {
    val updating = UpdateStatus.running
    val domains = remember(updating) { prefs.listsEntryCount }
    PageHeader(
        HushIcons.ShieldCheck,
        if (protectionOn) "You're all set" else "Almost there",
        if (protectionOn) "Hush is blocking ads and trackers. Anything you skipped stays on the Home screen checklist." else "Turn on site blocking from the Home screen whenever you're ready.",
    )
    ListGroup {
        SummaryRow("Site blocking", if (protectionOn) "On" else "Off", protectionOn)
        GroupDivider()
        SummaryRow("DNS server", "${provider.label}, encrypted", true)
        GroupDivider()
        SummaryRow(
            "Blocklists",
            if (updating) "Downloading…" else if (domains > 0) "${compactCount(domains)} domains" else "Not downloaded yet",
            !updating && domains > 0,
        )
        GroupDivider()
        SummaryRow("Private DNS", if (privateDns.bypassesHush) "Needs changing" else "OK", !privateDns.bypassesHush)
        GroupDivider()
        SummaryRow("Night lock", if (accessibilityOn) "Ready" else "Not set up", accessibilityOn)
    }
}

@Composable
private fun SummaryRow(title: String, value: String, ok: Boolean) {
    val status = statusColors
    GroupRow(
        title = title,
        trailing = {
            Text(value, style = MaterialTheme.typography.labelLarge, color = if (ok) status.protected else MaterialTheme.colorScheme.onSurfaceVariant)
        },
    )
}
