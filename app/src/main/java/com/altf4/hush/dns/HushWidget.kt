package com.altf4.hush.dns

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.util.SizeF
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.altf4.hush.MainActivity
import com.altf4.hush.R
import com.altf4.hush.data.Prefs
import com.altf4.hush.ui.compactCount

/**
 * Home screen widget: lookups blocked today, the share blocked, the size of the filter lists, a
 * week chart, and a button that turns site blocking on or off. On Android 12+ it picks one of
 * three layouts to suit the size it is given. It has no update timer; it is redrawn when
 * protection changes or the counts are saved.
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

    private class Facts(
        val on: Boolean,
        val blocked: Int,
        val percent: String,
        val domains: String,
        val week: String,
        val weekBlocked: List<Int>,
    )

    companion object {
        private const val ACTION_TOGGLE = "com.altf4.hush.WIDGET_TOGGLE"

        // Sizes (dp) the layouts are chosen at: compact below a wide cell row, wide, then tall.
        private val COMPACT = SizeF(110f, 110f)
        private val WIDE = SizeF(250f, 110f)
        private val LARGE = SizeF(250f, 220f)

        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, HushWidget::class.java))
            if (ids.isEmpty()) return
            manager.updateAppWidget(ids, render(context))
        }

        private fun render(context: Context): RemoteViews {
            val prefs = Prefs(context)
            val stats = Stats.current(prefs)
            val facts = Facts(
                on = VpnState.running,
                blocked = stats.todayBlocked,
                percent = if (stats.todayTotal > 0) "${Math.round(stats.todayBlocked * 100f / stats.todayTotal)}%" else "–",
                domains = if (prefs.listsEntryCount > 0) compactCount(prefs.listsEntryCount) else "–",
                week = compactCount(stats.weekBlocked.sum()),
                weekBlocked = stats.weekBlocked,
            )

            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val openApp = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), flags)
            // Turning on needs the system's VPN consent the first time, and only the app can ask.
            val toggle = if (!facts.on && ProtectionControl.needsConsent(context)) {
                openApp
            } else {
                PendingIntent.getBroadcast(
                    context, 1, Intent(context, HushWidget::class.java).setAction(ACTION_TOGGLE), flags,
                )
            }

            val wide = build(context, R.layout.widget_hush, facts, openApp, toggle)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return wide
            return RemoteViews(
                mapOf(
                    COMPACT to build(context, R.layout.widget_compact, facts, openApp, toggle),
                    WIDE to wide,
                    LARGE to build(context, R.layout.widget_large, facts, openApp, toggle),
                ),
            )
        }

        private fun build(
            context: Context,
            layout: Int,
            facts: Facts,
            openApp: PendingIntent,
            toggle: PendingIntent,
        ): RemoteViews {
            val on = facts.on
            fun color(id: Int) = ContextCompat.getColor(context, id)

            val views = RemoteViews(context.packageName, layout)
            views.setTextViewText(R.id.widget_status, context.getString(if (on) R.string.widget_protected else R.string.widget_paused))
            views.setTextColor(R.id.widget_status, color(if (on) R.color.widget_protected else R.color.widget_paused))
            views.setTextViewText(R.id.widget_count, compactCount(facts.blocked))
            views.setInt(R.id.widget_toggle, "setBackgroundResource", if (on) R.drawable.widget_button_on else R.drawable.widget_button_off)
            views.setInt(R.id.widget_toggle, "setColorFilter", color(if (on) R.color.widget_on_icon else R.color.widget_off_icon))
            views.setContentDescription(
                R.id.widget_toggle,
                context.getString(if (on) R.string.widget_turn_off else R.string.widget_turn_on),
            )

            if (layout != R.layout.widget_compact) {
                views.setTextViewText(R.id.widget_percent, facts.percent)
                views.setTextViewText(R.id.widget_domains, facts.domains)
            }
            if (layout == R.layout.widget_large) {
                views.setTextViewText(R.id.widget_week, facts.week)
                views.setImageViewBitmap(
                    R.id.widget_chart,
                    weekChart(facts.weekBlocked, color(R.color.widget_bar_past), color(R.color.widget_bar_today)),
                )
            }

            views.setOnClickPendingIntent(R.id.widget_root, openApp)
            views.setOnClickPendingIntent(R.id.widget_toggle, toggle)
            return views
        }

        /** Seven rounded bars, oldest first, today highlighted. Drawn small and stretched to fit. */
        private fun weekChart(blocked: List<Int>, past: Int, today: Int): Bitmap {
            val width = 560
            val height = 120
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val days = blocked.takeLast(7)
            val gap = 16f
            val barWidth = (width - gap * (days.size - 1)) / days.size
            val peak = maxOf(1, days.maxOrNull() ?: 1)
            days.forEachIndexed { i, value ->
                val barHeight = maxOf(10f, value * height.toFloat() / peak)
                val left = i * (barWidth + gap)
                paint.color = if (i == days.lastIndex) today else past
                canvas.drawRoundRect(
                    RectF(left, height - barHeight, left + barWidth, height.toFloat()),
                    barWidth / 3f, barWidth / 3f, paint,
                )
            }
            return bitmap
        }
    }
}
