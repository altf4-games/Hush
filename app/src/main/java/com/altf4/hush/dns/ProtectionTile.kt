package com.altf4.hush.dns

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.altf4.hush.MainActivity
import com.altf4.hush.R

/** Quick settings tile: shows whether site blocking is on and flips it with one tap. */
class ProtectionTile : TileService() {
    override fun onTileAdded() = draw(VpnState.running)

    override fun onStartListening() {
        listening = this
        draw(VpnState.running)
    }

    override fun onStopListening() {
        if (listening === this) listening = null
    }

    override fun onClick() {
        // From a locked screen, ask for the unlock first so a found phone cannot be switched off.
        if (isLocked) unlockAndRun { toggle() } else toggle()
    }

    private fun toggle() {
        val on = VpnState.running
        if (!on && ProtectionControl.needsConsent(this)) {
            openApp()
            return
        }
        ProtectionControl.toggle(this)
        // Show the new state at once; the tunnel confirms (or corrects it) when it has changed.
        draw(!on)
    }

    private fun draw(on: Boolean) {
        val tile = qsTile ?: return
        tile.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.tile_label)
        tile.icon = Icon.createWithResource(this, R.drawable.ic_tile)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = getString(if (on) R.string.tile_on else R.string.tile_off)
        }
        tile.updateTile()
    }

    @Suppress("DEPRECATION")
    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE),
            )
        } else {
            startActivityAndCollapse(intent)
        }
    }

    companion object {
        private val main = Handler(Looper.getMainLooper())

        /** The tile instance the system is currently showing, if any. Touched on the main thread. */
        @Volatile private var listening: ProtectionTile? = null

        /**
         * Redraws a tile that is already showing. While the panel is open the system keeps the tile
         * bound, so asking it to start listening again does nothing; it has to be drawn directly.
         */
        fun redraw() {
            main.post { listening?.draw(VpnState.running) }
        }
    }
}
