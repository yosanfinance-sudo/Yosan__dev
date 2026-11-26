package com.example.features.messages

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class YosanNotificationListenerService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val pkgName = sbn.packageName ?: return

        // Ignore notifications from our own application
        if (pkgName == applicationContext.packageName) return

        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString().orEmpty()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()

        val fullContent = listOf(title, text, subText, bigText).filter { it.isNotBlank() }.joinToString(" ")
        if (fullContent.isBlank()) return

        // Resolve friendly application label (e.g. "Google Pay", "PhonePe", "HDFC Bank")
        val appLabel = try {
            val pm = applicationContext.packageManager
            val ai = pm.getApplicationInfo(pkgName, 0)
            pm.getApplicationLabel(ai).toString()
        } catch (_: Exception) {
            pkgName.substringAfterLast(".")
        }

        serviceScope.launch {
            MessageIngestionManager.processIncomingMessage(
                context = applicationContext,
                rawText = fullContent,
                source = "Notification: $appLabel",
                timestamp = sbn.postTime
            )
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i("YosanNotifListener", "Yosan Notification Listener Service connected successfully.")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.i("YosanNotifListener", "Yosan Notification Listener Service disconnected.")
    }
}
