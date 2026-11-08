package com.altf4.hush.dns

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.altf4.hush.MainActivity
import com.altf4.hush.R

/** Quick settings tile: shows whether site blocking is on and flips it with one tap. */
class ProtectionTile : TileService() {
    override fun onStartListening() = draw()

    override fun onClick() {
        if (!VpnState.running && ProtectionControl.needsConsent(this)) {
            openApp()
            return
        }
        ProtectionControl.toggle(this)
        // The tunnel redraws the tile once it has actually started or stopped.
    }

    private fun draw() {
        val tile = qsTile ?: return
        val on = VpnState.running
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
}
