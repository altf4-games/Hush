package com.altf4.hush.ui

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.altf4.hush.dns.DnsProvider
import com.altf4.hush.dns.ListCatalog

/** What Hush is, how it works, what it stores, and where its data comes from. */
@Suppress("DEPRECATION")
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val version = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = ScreenPadding, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(CardGap),
    ) {
        item {
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
                Text("About", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            }
        }

        item {
            HushCard(shape = MaterialTheme.shapes.extraLarge) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    IconBadge(HushIcons.ShieldCheck, tint = statusColors.protected, container = statusColors.protectedContainer, size = 72)
                    Spacer(Modifier.height(16.dp))
                    Text("Hush", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onSurface)
                    if (version.isNotEmpty()) {
                        Text("Version $version", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        "Blocks ads, trackers and sites you choose on your whole phone, and closes distracting apps during quiet hours. It all runs on this device.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        }

        item { SectionLabel("How it works") }
        item {
            ListGroup {
                Fact(
                    HushIcons.Shield,
                    "Only DNS goes through Hush",
                    "A local VPN carries just the lookups apps make to find servers. All other traffic goes straight out as usual.",
                )
                GroupDivider()
                Fact(
                    HushIcons.Filter,
                    "Blocked names get an empty answer",
                    "Ads and trackers on your lists never load, in any app or browser.",
                )
                GroupDivider()
                Fact(
                    HushIcons.Dns,
                    "Everything else is encrypted",
                    "Allowed lookups go to your chosen DNS server over TLS, never in the clear.",
                )
                GroupDivider()
                Fact(
                    HushIcons.Moon,
                    "Night lock",
                    "An accessibility service notices which app opened and closes the ones you chose during quiet hours. It cannot read the screen.",
                )
            }
        }

        item { SectionLabel("Privacy") }
        item {
            ListGroup {
                Fact(HushIcons.ShieldCheck, "No account, ads or analytics", "Hush has no servers of its own and sends nothing about you anywhere.")
                GroupDivider()
                Fact(HushIcons.Chart, "No browsing history", "The names you look up are never saved or logged. Only daily counts are kept, for the charts.")
                GroupDivider()
                Fact(
                    HushIcons.Dns,
                    "Who Hush talks to",
                    DnsProvider.entries.joinToString("; ") { "${it.label}: ${it.addressText}" } +
                        " (port ${DnsProvider.PORT}, only the one you choose), plus the list hosts you switch on.",
                )
            }
        }

        item { SectionLabel("Filter lists") }
        item {
            ListGroup {
                Fact(
                    HushIcons.Filter,
                    "${ListCatalog.all.size} lists to choose from",
                    ListCatalog.all.joinToString(", ") { it.name } + ". Downloaded over HTTPS from fixed addresses built into the app.",
                )
            }
        }

        item { SectionLabel("Good to know") }
        item {
            ListGroup {
                Fact(
                    HushIcons.Bolt,
                    "Limits of DNS blocking",
                    "Ads served from the same address as the content cannot be removed, and apps with their own DNS skip Hush. " +
                        "Set Private DNS to Off or Automatic and turn off Secure DNS in your browser.",
                )
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun Fact(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String) {
    GroupRow(title = title, subtitle = body, leading = { IconBadge(icon, size = 36) })
}
