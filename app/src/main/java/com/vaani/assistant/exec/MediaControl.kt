package com.vaani.assistant.exec

import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import com.vaani.assistant.core.Vlog
import com.vaani.assistant.nlu.MediaAction

/**
 * Play / pause / next / previous without touching any app's UI. Works with Spotify, YouTube
 * Music, local players — anything that publishes a MediaSession. Costs no memory and cannot
 * mis-tap, so it is always tried before falling back to screen automation.
 */
class MediaControl(private val ctx: Context) {

    private fun controller(): MediaController? {
        return runCatching {
            val msm = ctx.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val listener = ComponentName(ctx, VaaniNotificationListener::class.java)
            val sessions = msm.getActiveSessions(listener)
            val session = sessions.firstOrNull { it.packageName != ctx.packageName }
                ?: sessions.firstOrNull()
                ?: return null
            MediaController(ctx, session.sessionToken)
        }.getOrElse {
            Vlog.e("Media", "no session: ${it.message}")
            null
        }
    }

    fun apply(action: MediaAction): Boolean {
        val c = controller() ?: return false
        val controls = c.transportControls
        return runCatching {
            when (action) {
                MediaAction.PLAY -> controls.play()
                MediaAction.PAUSE -> controls.pause()
                MediaAction.STOP -> controls.stop()
                MediaAction.NEXT -> controls.skipToNext()
                MediaAction.PREV -> controls.skipToPrevious()
                MediaAction.TOGGLE -> {
                    if (c.playbackState?.state == PlaybackState.STATE_PLAYING) controls.pause() else controls.play()
                }
            }
            true
        }.getOrElse {
            Vlog.e("Media", "transport failed: ${it.message}")
            false
        }
    }

    fun volume(up: Boolean): Boolean = runCatching {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            if (up) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
            AudioManager.FLAG_SHOW_UI
        )
        true
    }.getOrDefault(false)

    fun isPlaying(): Boolean = controller()?.playbackState?.state == PlaybackState.STATE_PLAYING
}
