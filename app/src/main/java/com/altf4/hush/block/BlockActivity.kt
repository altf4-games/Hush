package com.altf4.hush.block

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
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
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.altf4.hush.data.Prefs
import com.altf4.hush.ui.HushTheme
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** What the block screen offers about the emergency pass. [phase] is null when there is no app to ask about. */
private class PassView(val phase: EmergencyPass.Phase?, val waitSeconds: Int)

/**
 * The calm screen shown after a chosen app is sent home. It does not say which app it was, shows
 * when the window ends, and closes itself shortly after (one delayed call, only while visible).
 * It also offers the emergency pass unless strict mode is on; see [EmergencyPass].
 */
class BlockActivity : ComponentActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private val closeLater = Runnable { finish() }
    private val prefs by lazy { Prefs(applicationContext) }

    // The app that was just closed. Held only while this screen exists; never stored.
    private var target: String? = null
    private var pass by mutableStateOf(PassView(null, 0))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        target = intent?.getStringExtra(EXTRA_PACKAGE)
        refreshPass()
        val until = prefs.schedule().nextChange(ZonedDateTime.now())
            ?.format(DateTimeFormatter.ofPattern("HH:mm"))
        setContent {
            HushTheme(prefs.themeMode) {
                QuietScreen(
                    until = until,
                    pass = pass,
                    strict = prefs.strictMode,
                    onRequest = ::requestPass,
                    onUse = ::usePass,
                    onClose = { finish() },
                )
            }
        }
        scheduleClose()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        target = intent.getStringExtra(EXTRA_PACKAGE)
        refreshPass()
        scheduleClose() // shown again: start the countdown over
    }

    override fun onDestroy() {
        handler.removeCallbacks(closeLater)
        super.onDestroy()
    }

    private fun refreshPass() {
        val pkg = target
        if (pkg == null) {
            pass = PassView(null, 0)
            return
        }
        val now = SystemClock.elapsedRealtime()
        val phase = EmergencyPass.shared.phase(pkg, prefs.strictMode, now)
        val seconds = ((EmergencyPass.shared.waitLeft(pkg, now) + 999) / 1000).toInt()
        pass = PassView(phase, seconds)
    }

    private fun requestPass() {
        val pkg = target ?: return
        EmergencyPass.shared.request(pkg, prefs.strictMode, SystemClock.elapsedRealtime())
        refreshPass()
        scheduleClose()
    }

    private fun usePass() {
        val pkg = target ?: return
        if (!EmergencyPass.shared.grant(pkg, prefs.strictMode, SystemClock.elapsedRealtime())) {
            refreshPass()
            return
        }
        try {
            packageManager.getLaunchIntentForPackage(pkg)?.let { launch ->
                startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        } catch (e: RuntimeException) {
            // The app is gone or cannot be started; nothing to do but close.
        }
        finish()
    }

    private fun scheduleClose() {
        handler.removeCallbacks(closeLater)
        handler.postDelayed(closeLater, AUTO_CLOSE_MS)
    }

    companion object {
        const val EXTRA_PACKAGE = "com.altf4.hush.extra.PACKAGE"
        private const val AUTO_CLOSE_MS = 6_000L
    }
}

@Composable
private fun QuietScreen(
    until: String?,
    pass: PassView,
    strict: Boolean,
    onRequest: () -> Unit,
    onUse: () -> Unit,
    onClose: () -> Unit,
) {
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
        Button(onClick = onClose) { Text("OK") }
        when (pass.phase) {
            EmergencyPass.Phase.CanRequest -> TextButton(onClick = onRequest) {
                Text("Need it? Ask for a 5-minute pass")
            }
            EmergencyPass.Phase.Waiting -> Text(
                text = "A pass will be ready in about ${pass.waitSeconds} seconds. Open the app again then.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp),
            )
            EmergencyPass.Phase.Ready -> TextButton(onClick = onUse) {
                Text("Use the 5-minute pass")
            }
            EmergencyPass.Phase.Unavailable -> if (strict) Text(
                text = "Strict mode is on.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp),
            )
            null -> Unit
        }
    }
}
