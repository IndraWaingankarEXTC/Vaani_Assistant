package com.vaani.assistant

import android.app.Application
import com.vaani.assistant.core.CrashLog
import com.vaani.assistant.core.Prefs

/** Runs before anything else, so a crash during startup is still recorded. */
class VaaniApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        CrashLog.install(this)
    }
}
