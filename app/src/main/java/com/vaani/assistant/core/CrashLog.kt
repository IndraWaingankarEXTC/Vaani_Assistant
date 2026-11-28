package com.vaani.assistant.core

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Saves the last crash to a file so it can be read back on the next launch.
 *
 * On a phone with no computer attached there is no `adb logcat`, so a crash on startup is normally
 * invisible — the app just closes. This writes the exception and its first frames to the app's own
 * directory, and the app shows it at the top of its log the next time it opens.
 */
object CrashLog {

    private const val FILE = "last-crash.txt"

    fun install(context: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        val app = context.applicationContext
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                val text = buildString {
                    appendLine("Vaani stopped on $stamp")
                    appendLine()
                    appendLine(throwable.toString())
                    throwable.stackTrace.take(30).forEach { appendLine("  at $it") }
                    throwable.cause?.let { cause ->
                        appendLine()
                        appendLine("Caused by: $cause")
                        cause.stackTrace.take(15).forEach { appendLine("  at $it") }
                    }
                }
                File(app.filesDir, FILE).writeText(text)
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    /** Read and clear the stored crash, if there is one. */
    fun take(context: Context): String? {
        val file = File(context.filesDir, FILE)
        if (!file.exists()) return null
        return runCatching {
            val text = file.readText()
            file.delete()
            text
        }.getOrNull()
    }
}
