package com.altf4.hush.data

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import java.util.concurrent.atomic.AtomicBoolean

object TrustedTime {
    private const val FILE = "hush_time"
    private const val STALE_MS = 3L * 60 * 60 * 1000
    private const val RETRY_MS = 2L * 60 * 1000
    private const val TIMEOUT_MS = 4000

    private val hosts = listOf("time.google.com", "time.cloudflare.com", "time.android.com")
    private val urls = listOf("https://1.1.1.1/", "https://8.8.8.8/")

    @Volatile private var clock: TrustedClock? = null
    private val running = AtomicBoolean(false)
    @Volatile private var lastAttempt = -RETRY_MS

    fun init(context: Context) {
        if (clock != null) return
        val app = context.applicationContext
        val sp = app.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val store = object : TimeStore {
            override fun load(): TimeAnchor? {
                if (!sp.contains("server")) return null
                return TimeAnchor(
                    sp.getInt("boot", -1),
                    sp.getLong("server", 0),
                    sp.getLong("elapsed", 0),
                    sp.getLong("skew", 0),
                )
            }

            override fun save(anchor: TimeAnchor) {
                sp.edit()
                    .putInt("boot", anchor.boot)
                    .putLong("server", anchor.serverMs)
                    .putLong("elapsed", anchor.elapsedMs)
                    .putLong("skew", anchor.skewMs)
                    .apply()
            }
        }
        clock = TrustedClock(
            wall = System::currentTimeMillis,
            elapsed = SystemClock::elapsedRealtime,
            boot = { Settings.Global.getInt(app.contentResolver, Settings.Global.BOOT_COUNT, -1) },
            store = store,
        )
    }

    fun now(): Long = clock?.now() ?: System.currentTimeMillis()

    val synced: Boolean get() = clock?.synced == true

    fun syncIfStale() {
        val c = clock ?: return
        val sinceAttempt = SystemClock.elapsedRealtime() - lastAttempt
        if (c.ageMs() < STALE_MS || sinceAttempt < RETRY_MS) return
        if (!running.compareAndSet(false, true)) return
        lastAttempt = SystemClock.elapsedRealtime()
        Thread {
            try {
                fetch()?.let { c.record(it) }
            } finally {
                running.set(false)
            }
        }.apply { isDaemon = true }.start()
    }

    private fun fetch(): Long? {
        val elapsed = SystemClock::elapsedRealtime
        for (host in hosts) {
            val t = try { Sntp.query(host, TIMEOUT_MS, elapsed) } catch (e: Exception) { null }
            if (t != null) return t
        }
        for (url in urls) {
            val t = try { HttpsDate.query(url, TIMEOUT_MS, elapsed) } catch (e: Exception) { null }
            if (t != null) return t
        }
        return null
    }
}
