package com.vaani.assistant.qs

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.vaani.assistant.R
import com.vaani.assistant.core.Vlog
import com.vaani.assistant.lens.LensActivity

/**
 * One tap from the shade straight into the camera lens.
 *
 * Starting an activity from a tile is a user action, so it is allowed — but the intent shape
 * changed in Android 14, where it must be wrapped in a PendingIntent.
 */
class LensTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            label = "Vaani Lens"
            icon = Icon.createWithResource(this@LensTileService, R.drawable.ic_launcher)
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        // unlockAndRun: a tile can be tapped from the lock screen, where activities cannot start.
        unlockAndRun { openLens() }
    }

    private fun openLens() {
        val intent = Intent(this, LensActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startActivityAndCollapse(
                    PendingIntent.getActivity(
                        this, 0, intent,
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
        }.onFailure { Vlog.e("Tile", "lens failed: ${it.message}") }
    }
}
