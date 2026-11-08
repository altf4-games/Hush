package com.altf4.hush.dns

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.altf4.hush.data.Prefs
import com.altf4.hush.data.StatsLog
import java.util.TimeZone

/** What the dashboard shows. Counts only; no names are ever kept. */
class StatsView(
    val todayTotal: Int,
    val todayBlocked: Int,
    val lifetimeTotal: Long,
    val lifetimeBlocked: Long,
    /** Lookups for each of the last seven days, oldest first. */
    val weekBlocked: List<Int>,
    val weekTotal: List<Int>,
) {
    companion object {
        val EMPTY = StatsView(0, 0, 0, 0, List(StatsLog.KEEP_DAYS) { 0 }, List(StatsLog.KEEP_DAYS) { 0 })
    }
}

/**
 * Counters for the dashboard. [record] is called for every lookup on the tunnel threads, so it is
 * cheap: a lock, two increments, and (at most about once a second) a refresh of the state the UI
 * reads. Saved to settings every few hundred lookups and when the tunnel stops. No timers.
 */
object Stats {
    var view by mutableStateOf(StatsView.EMPTY)
        private set

    /** Called, with the lock held, after the counts are saved or cleared. Must return quickly. */
    @Volatile var onSaved: (() -> Unit)? = null

    private val lock = Any()
    private var log = StatsLog()
    private var prefs: Prefs? = null
    private var loaded = false
    private var sinceSave = 0
    private var lastPublish = 0L
    private var lastSave = 0L
    private var cachedDay = 0
    private var dayCheckedAt = 0L

    /** Loads the saved counts the first time; later calls only remember [prefs]. */
    fun attach(prefs: Prefs) {
        synchronized(lock) {
            this.prefs = prefs
            if (!loaded) {
                log = StatsLog.decode(prefs.statsRaw)
                loaded = true
            }
            publish(System.currentTimeMillis())
        }
    }

    fun record(blocked: Boolean) {
        val now = System.currentTimeMillis()
        synchronized(lock) {
            log.record(today(now), blocked)
            sinceSave++
            if (now - lastPublish >= PUBLISH_MS) publish(now)
            if (sinceSave >= SAVE_EVERY || (sinceSave > 0 && now - lastSave >= SAVE_MS)) save(now)
        }
    }

    fun flush() {
        val now = System.currentTimeMillis()
        synchronized(lock) {
            publish(now)
            if (sinceSave > 0) save(now)
        }
    }

    fun reset() {
        synchronized(lock) {
            log.clear()
            sinceSave = 0
            prefs?.statsRaw = null
            publish(System.currentTimeMillis())
            onSaved?.invoke()
        }
    }

    private fun today(now: Long): Int {
        // The zone offset only matters at midnight; look again once a minute.
        if (dayCheckedAt == 0L || now - dayCheckedAt > 60_000) {
            cachedDay = StatsLog.dayOf(now, TimeZone.getDefault().getOffset(now))
            dayCheckedAt = now
        }
        return cachedDay
    }

    /** Today's counts as they stand now, for places outside the dashboard (the home screen widget). */
    fun current(prefs: Prefs): StatsView {
        val now = System.currentTimeMillis()
        synchronized(lock) {
            return if (loaded) viewOf(log, now) else viewOf(StatsLog.decode(prefs.statsRaw), now)
        }
    }

    private fun publish(now: Long) {
        lastPublish = now
        view = viewOf(log, now)
    }

    private fun viewOf(log: StatsLog, now: Long): StatsView {
        val day = StatsLog.dayOf(now, TimeZone.getDefault().getOffset(now))
        val week = log.last(day)
        val today = log.on(day)
        return StatsView(
            todayTotal = today.total,
            todayBlocked = today.blocked,
            lifetimeTotal = log.lifetimeTotal,
            lifetimeBlocked = log.lifetimeBlocked,
            weekBlocked = week.map { it.blocked },
            weekTotal = week.map { it.total },
        )
    }

    private fun save(now: Long) {
        sinceSave = 0
        lastSave = now
        prefs?.statsRaw = log.encode()
        onSaved?.invoke()
    }

    private const val PUBLISH_MS = 1_000L
    private const val SAVE_EVERY = 200
    private const val SAVE_MS = 60_000L
}
