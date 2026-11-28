package com.vaani.assistant.qs

import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.vaani.assistant.R
import com.vaani.assistant.core.Prefs
import com.vaani.assistant.core.Vlog
import com.vaani.assistant.voice.VoiceService

/**
 * Vaani in the notification shade, next to Bluetooth and Wi-Fi.
 *
 * Tap: listening on or off. When it is on, the wake word works from any app and everything else is
 * done by voice — no need to open Vaani itself.
 *
 * Long-press is the system's, not ours: Android opens the app's info page for every tile that is
 * not a system app. Bluetooth gets its own pairing sheet because Bluetooth *is* the system.
 */
class VaaniTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        refresh()
    }

    override fun onClick() {
        super.onClick()
        val turningOn = !Prefs.wakeWordEnabled
        Vlog.i("Tile", if (turningOn) "switching listening on" else "switching listening off")
        runCatching {
            if (turningOn) {
                Prefs.wakeWordEnabled = true
                VoiceService.setHandsFree(this, true)
                VoiceService.start(this)
            } else {
                Prefs.wakeWordEnabled = false
                VoiceService.setHandsFree(this, false)
                VoiceService.stop(this)
            }
        }.onFailure { Vlog.e("Tile", "toggle failed: ${it.message}") }
        refresh()
    }

    private fun refresh() {
        val tile = qsTile ?: return
        val on = Prefs.wakeWordEnabled
        tile.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "Vaani"
        tile.icon = Icon.createWithResource(this, R.drawable.ic_launcher)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (on) "Listening" else "Off"
        }
        tile.updateTile()
    }
}
