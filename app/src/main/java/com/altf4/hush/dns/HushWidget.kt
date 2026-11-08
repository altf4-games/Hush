package com.altf4.hush.dns

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.altf4.hush.MainActivity
import com.altf4.hush.R
import com.altf4.hush.data.Prefs
import com.altf4.hush.ui.compactCount

/**
 * Home screen widget: lookups blocked today and a button that turns site blocking on or off.
 * It has no update timer; it is redrawn when protection changes or the counts are saved.
 */
class HushWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { manager.updateAppWidget(it, render(context)) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_TOGGLE -> {
                ProtectionControl.toggle(context)
                refresh(context)
            }
            Intent.ACTION_DATE_CHANGED -> refresh(context) // a new day starts at zero
            else -> super.onReceive(context, intent)
        }
    }

    companion object {
        private const val ACTION_TOGGLE = "com.altf4.hush.WIDGET_TOGGLE"

        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, HushWidget::class.java))
            if (ids.isEmpty()) return
            manager.updateAppWidget(ids, render(context))
        }

        private fun render(context: Context): RemoteViews {
            val on = VpnState.running
            val blocked = Stats.current(Prefs(context)).todayBlocked
            fun color(id: Int) = ContextCompat.getColor(context, id)

            val views = RemoteViews(context.packageName, R.layout.widget_hush)
            views.setTextViewText(R.id.widget_status, context.getString(if (on) R.string.widget_protected else R.string.widget_paused))
            views.setTextColor(R.id.widget_status, color(if (on) R.color.widget_protected else R.color.widget_paused))
            views.setTextViewText(R.id.widget_count, compactCount(blocked))
            views.setInt(R.id.widget_toggle, "setBackgroundResource", if (on) R.drawable.widget_button_on else R.drawable.widget_button_off)
            views.setInt(R.id.widget_toggle, "setColorFilter", color(if (on) R.color.widget_on_icon else R.color.widget_off_icon))
            views.setContentDescription(
                R.id.widget_toggle,
                context.getString(if (on) R.string.widget_turn_off else R.string.widget_turn_on),
            )

            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val openApp = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), flags)
            // Turning on needs the system's VPN consent the first time, and only the app can ask.
            val toggle = if (!on && ProtectionControl.needsConsent(context)) {
                openApp
            } else {
                PendingIntent.getBroadcast(
                    context, 1, Intent(context, HushWidget::class.java).setAction(ACTION_TOGGLE), flags,
                )
            }
            views.setOnClickPendingIntent(R.id.widget_root, openApp)
            views.setOnClickPendingIntent(R.id.widget_toggle, toggle)
            return views
        }
    }
}
