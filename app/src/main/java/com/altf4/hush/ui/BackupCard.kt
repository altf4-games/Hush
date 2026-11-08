package com.altf4.hush.ui

import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.altf4.hush.data.BackupResult
import com.altf4.hush.data.Prefs
import com.altf4.hush.data.SettingsBackup
import java.io.IOException

/** The last backup message. Global (like UpdateStatus) so it survives the screen being rebuilt after a load. */
object BackupStatus {
    var message by mutableStateOf<String?>(null)
}

/**
 * Save the settings to a file, or load them back. The person picks the file with the system file
 * picker, so the app needs no storage permission, and nothing is sent anywhere.
 */
@Composable
fun BackupCard(prefs: Prefs, onRestored: () -> Unit) {
    val appContext = LocalContext.current.applicationContext
    val main = remember { Handler(Looper.getMainLooper()) }

    val export = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri: Uri? ->
        if (uri != null) {
            val text = SettingsBackup.encode(prefs.snapshot())
            Thread({
                BackupStatus.message = try {
                    appContext.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { it.write(text) }
                        ?: throw IOException("no stream")
                    "Settings saved"
                } catch (e: IOException) {
                    "Could not save the file"
                } catch (e: RuntimeException) {
                    "Could not save the file"
                }
            }, "hush-export").start()
        }
    }

    val import = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            Thread({
                val outcome = readBackup(appContext, uri)
                var restored = false
                BackupStatus.message = when (outcome) {
                    is BackupResult.Refused -> "Not loaded: ${outcome.reason}"
                    is BackupResult.Ok -> if (prefs.restore(outcome.snapshot)) {
                        restored = true
                        "Settings loaded"
                    } else {
                        "Not loaded: settings are locked until quiet hours end"
                    }
                }
                if (restored) main.post { onRestored() }
            }, "hush-import").start()
        }
    }

    HushCard {
        Text(
            text = "Backup",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "Save your choices to a file, or load them on another phone. Lists are downloaded again; nothing about your browsing is included.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { export.launch("hush-settings.txt") }) { Text("Save to file") }
            OutlinedButton(onClick = { import.launch(arrayOf("text/plain", "application/octet-stream")) }) {
                Text("Load from file")
            }
        }
        val note = BackupStatus.message
        if (note != null) {
            Text(
                text = note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

/** Reads at most [SettingsBackup.MAX_CHARS] characters; a larger or unreadable file is refused. */
private fun readBackup(context: android.content.Context, uri: Uri): BackupResult {
    return try {
        val reader = context.contentResolver.openInputStream(uri)?.bufferedReader()
            ?: return BackupResult.Refused("The file could not be opened")
        reader.use {
            val buffer = CharArray(SettingsBackup.MAX_CHARS + 1)
            var length = 0
            while (length < buffer.size) {
                val n = it.read(buffer, length, buffer.size - length)
                if (n < 0) break
                length += n
            }
            if (length > SettingsBackup.MAX_CHARS) {
                BackupResult.Refused("The file is too large")
            } else {
                SettingsBackup.decode(String(buffer, 0, length))
            }
        }
    } catch (e: IOException) {
        BackupResult.Refused("The file could not be read")
    } catch (e: RuntimeException) {
        BackupResult.Refused("The file could not be read")
    }
}
