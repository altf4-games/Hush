package com.altf4.hush.block

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import com.altf4.hush.data.NightClock
import com.altf4.hush.data.Prefs
import com.altf4.hush.data.Schedule

/**
 * Sends the chosen apps back to the home screen during quiet hours. Event-driven: it only listens
 * to window changes (see accessibility_config.xml), reads nothing but the package name of the
 * window that opened, compares it with the chosen set, and forgets it. It cannot read screen
 * content, and it never stores or logs which app was seen.
 */
class AppBlockerService : AccessibilityService() {
    @Volatile private var blocked: Set<String> = emptySet()
    @Volatile private var clock: NightClock = NightClock(Schedule.OFF)

    private val prefs by lazy { Prefs(applicationContext) }
    private var versionHandle: AutoCloseable? = null
    private var lastScreenAt = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        reload()
        // Settings changes bump a version counter; reload only when it changes.
        versionHandle = prefs.registerVersionListener { reload() }
        BlockerState.update(true)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val current = clock
        val opened = event.packageName?.toString()
        val shouldBlock = BlockDecision.shouldBlock(
            pkg = opened,
            ownPackage = packageName,
            blocked = blocked,
            hasPass = { EmergencyPass.shared.isActive(it, SystemClock.elapsedRealtime()) },
            nightActive = { current.isActive() },
        )
        if (!shouldBlock || opened == null) return
        performGlobalAction(GLOBAL_ACTION_HOME)
        showBlockScreen(opened)
    }

    override fun onInterrupt() {
        // Nothing to interrupt: there is no ongoing feedback.
    }

    override fun onUnbind(intent: Intent?): Boolean {
        stopListening()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        stopListening()
        super.onDestroy()
    }

    private fun reload() {
        blocked = prefs.blockedApps()
        clock = NightClock(prefs.schedule())
    }

    private fun stopListening() {
        versionHandle?.close()
        versionHandle = null
        BlockerState.update(false)
    }

    /** [opened] goes to our own block screen as a transient extra (for the emergency pass); never stored. */
    private fun showBlockScreen(opened: String) {
        // One screen per burst of events; the user may bounce off several windows in a row.
        val now = SystemClock.elapsedRealtime()
        if (now - lastScreenAt < SCREEN_DEBOUNCE_MS) return
        lastScreenAt = now
        try {
            startActivity(
                Intent(this, BlockActivity::class.java)
                    .putExtra(BlockActivity.EXTRA_PACKAGE, opened)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION),
            )
        } catch (e: RuntimeException) {
            // The home action already happened; the calm screen is a courtesy, never a crash.
        }
    }

    private companion object {
        const val SCREEN_DEBOUNCE_MS = 700L
    }
}
