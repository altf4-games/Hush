package com.altf4.hush.block

import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telecom.TelecomManager
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

/** One app the person can choose to close during quiet hours. */
class AppEntry(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap?,
    /** Usually needed at any hour; left unticked by "select all". */
    val essential: Boolean,
)

/**
 * Lists the apps that have a launcher icon, found with the `<queries>` launcher intent (no
 * QUERY_ALL_PACKAGES). Excludes this app, every home launcher and the phone app so the person
 * cannot lock themselves out. The list is built on demand and never stored.
 */
object InstalledApps {
    private const val ICON_PX = 96

    /** Blocking (loads icons); call off the main thread. */
    fun load(context: Context): List<AppEntry> {
        val pm = context.packageManager

        val excluded = HashSet<String>()
        excluded.add(context.packageName)
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        for (info in pm.queryIntentActivities(home, 0)) excluded.add(info.activityInfo.packageName)
        for (info in pm.queryIntentActivities(Intent(Intent.ACTION_DIAL), 0)) {
            excluded.add(info.activityInfo.packageName)
        }
        context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage?.let { excluded.add(it) }

        val deviceDefaults = HashSet<String>()
        Telephony.Sms.getDefaultSmsPackage(context)?.let { deviceDefaults.add(it) }

        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val seen = HashSet<String>()
        val entries = ArrayList<AppEntry>()
        for (info in pm.queryIntentActivities(launcher, 0)) {
            val pkg = info.activityInfo.packageName
            if (pkg in excluded || !seen.add(pkg)) continue
            val icon = try {
                info.loadIcon(pm).toBitmap(ICON_PX, ICON_PX).asImageBitmap()
            } catch (e: RuntimeException) {
                null
            }
            entries.add(
                AppEntry(
                    packageName = pkg,
                    label = info.loadLabel(pm).toString(),
                    icon = icon,
                    essential = EssentialApps.isEssential(pkg, deviceDefaults),
                ),
            )
        }
        entries.sortBy { it.label.lowercase() }
        return entries
    }
}
