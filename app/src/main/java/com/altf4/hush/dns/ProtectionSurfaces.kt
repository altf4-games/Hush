package com.altf4.hush.dns

import android.content.ComponentName
import android.content.Context
import android.service.quicksettings.TileService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Redraws the places that show protection outside the app: the home screen widget and the quick
 * settings tile. Called when protection starts or stops and when the counts are saved. Requests
 * are merged and run on a small thread, so callers on the tunnel threads never wait on it.
 */
object ProtectionSurfaces {
    private val pending = AtomicBoolean(false)
    private val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "hush-surfaces").apply { isDaemon = true } }

    fun refresh(context: Context) {
        val app = context.applicationContext
        if (!pending.compareAndSet(false, true)) return
        executor.execute {
            pending.set(false)
            try {
                HushWidget.refresh(app)
                ProtectionTile.redraw()
                TileService.requestListeningState(app, ComponentName(app, ProtectionTile::class.java))
            } catch (e: RuntimeException) {
                // A launcher or the system UI that is not ready; the next change redraws it.
            }
        }
    }
}
