package com.altf4.hush.block

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.altf4.hush.data.Prefs
import com.altf4.hush.ui.HushTheme
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * The calm screen shown after a chosen app is sent home. It does not say which app it was, shows
 * when the window ends, and closes itself shortly after (one delayed call, only while visible).
 */
class BlockActivity : ComponentActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private val closeLater = Runnable { finish() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = Prefs(applicationContext)
        val until = prefs.schedule().nextChange(ZonedDateTime.now())
            ?.format(DateTimeFormatter.ofPattern("HH:mm"))
        setContent {
            HushTheme(prefs.themeMode) {
                QuietScreen(until = until, onClose = { finish() })
            }
        }
        scheduleClose()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        scheduleClose() // shown again: start the countdown over
    }

    override fun onDestroy() {
        handler.removeCallbacks(closeLater)
        super.onDestroy()
    }

    private fun scheduleClose() {
        handler.removeCallbacks(closeLater)
        handler.postDelayed(closeLater, AUTO_CLOSE_MS)
    }

    private companion object {
        const val AUTO_CLOSE_MS = 6_000L
    }
}

@Composable
private fun QuietScreen(until: String?, onClose: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = "Quiet hours",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (until != null) "This is closed until $until." else "This is closed for now.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        TextButton(onClick = onClose) { Text("OK") }
    }
}
