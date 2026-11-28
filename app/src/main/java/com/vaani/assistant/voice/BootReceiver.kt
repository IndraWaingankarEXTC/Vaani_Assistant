package com.vaani.assistant.voice

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.vaani.assistant.core.Prefs

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        Prefs.init(context)
        if (Prefs.serviceEnabled) {
            runCatching { VoiceService.start(context) }
        }
    }
}
