package com.altf4.hush.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.altf4.hush.data.PrivateDnsMode

/** Reads the system Private DNS mode. Only "strict" (a named provider) bypasses Hush. */
fun readPrivateDns(context: Context): PrivateDnsMode =
    try {
        PrivateDnsMode.fromSetting(Settings.Global.getString(context.contentResolver, "private_dns_mode"))
    } catch (e: SecurityException) {
        PrivateDnsMode.Unknown
    }

/** True if the system will not put Hush to sleep to save battery. */
fun isBatteryUnrestricted(context: Context): Boolean =
    context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true

/**
 * Opens a system settings page, trying each intent in turn. There is no public intent for the
 * Private DNS page, so callers pass network settings with plain Settings as the fallback.
 */
fun openSettings(context: Context, vararg intents: Intent): Boolean {
    for (intent in intents) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return true
        } catch (e: ActivityNotFoundException) {
            // Try the next one.
        } catch (e: SecurityException) {
            // Try the next one.
        }
    }
    return false
}

fun openNetworkSettings(context: Context) =
    openSettings(context, Intent(Settings.ACTION_WIRELESS_SETTINGS), Intent(Settings.ACTION_SETTINGS))

fun openAccessibilitySettings(context: Context) =
    openSettings(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS), Intent(Settings.ACTION_SETTINGS))

fun openBatterySettings(context: Context) =
    openSettings(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS), Intent(Settings.ACTION_SETTINGS))

/** Hush's own App info page, where "Allow restricted settings" lives on Android 13+. */
fun openAppInfo(context: Context) =
    openSettings(
        context,
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")),
        Intent(Settings.ACTION_SETTINGS),
    )

/**
 * A number that goes up every time the screen resumes, e.g. on return from system settings. Use
 * it as a `remember` key to re-read system state.
 */
@Suppress("DEPRECATION")
@Composable
fun rememberResumeCount(): Int {
    val owner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    var count by remember { mutableIntStateOf(0) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) count++
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return count
}
