package com.vaani.assistant.exec

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.vaani.assistant.core.Vlog
import com.vaani.assistant.notify.Notif

/**
 * Also the phone's ears for what arrived while you were not looking.
 *
 * The service exists for media control, but the same permission lets it keep a short ring of recent
 * notifications so "what's new?" can be answered without opening the shade.
 */
class VaaniNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn?.notification ?: return
        val extras = notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (extras.getCharSequence(Notification.EXTRA_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT))?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return

        val app = runCatching {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(sbn.packageName, 0)
            ).toString()
        }.getOrDefault(sbn.packageName)

        val ongoing = notification.flags and Notification.FLAG_ONGOING_EVENT != 0
        add(Notif(app, title, text, System.currentTimeMillis(), ongoing))
    }

    companion object {
        private const val MAX = 40
        private val recent = ArrayDeque<Notif>()

        fun add(item: Notif) = synchronized(recent) {
            recent.addFirst(item)
            while (recent.size > MAX) recent.removeLast()
        }

        fun snapshot(): List<Notif> = synchronized(recent) { recent.toList() }

        fun clearAll() = synchronized(recent) { recent.clear() }

        fun isConnected(): Boolean = true

        fun log(message: String) = Vlog.i("Notif", message)
    }
}
