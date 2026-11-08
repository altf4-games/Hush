package com.altf4.hush.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.altf4.hush.data.Prefs

/** Switches for the protections that work on DNS answers, plus a note on encrypted DNS. */
@Composable
fun ProtectionCard(prefs: Prefs) {
    var uncloak by remember { mutableStateOf(prefs.uncloakCname) }
    var rebinding by remember { mutableStateOf(prefs.blockRebinding) }
    var bypass by remember { mutableStateOf(prefs.blockBypass) }

    HushCard {
        Text(
            text = "Encrypted DNS",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "Lookups go to Quad9 over TLS, and the certificate is checked. If it cannot be reached, lookups fail instead of going out unencrypted.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
        )
        SettingRow(
            title = "Block disguised trackers",
            description = "Blocks a name that is really an alias for a blocked tracker (CNAME cloaking).",
            checked = uncloak,
            onChange = {
                uncloak = it
                prefs.uncloakCname = it
            },
        )
        SettingRow(
            title = "Block DNS rebinding",
            description = "Blocks public names that answer with a private or local address. Add a site under Always allowed to exempt it.",
            checked = rebinding,
            onChange = {
                rebinding = it
                prefs.blockRebinding = it
            },
        )
        SettingRow(
            title = "Block secure-DNS bypass",
            description = "Blocks well-known secure-DNS provider names and the Firefox opt-out signal. May break browsers set to a custom provider.",
            checked = bypass,
            onChange = {
                bypass = it
                prefs.blockBypass = it
            },
        )
    }
}

@Composable
private fun SettingRow(
    title: String,
    description: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
