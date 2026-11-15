package com.locked.app

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Optional. During focus, notifications from locked apps are snoozed until the focus phase
 * ends, so they reappear at the break instead of pulling you back in.
 */
class LockNotificationListener : NotificationListenerService() {

    companion object {
        @Volatile
        var instance: LockNotificationListener? = null
    }

    override fun onListenerConnected() {
        instance = this
        onTick()
    }

    override fun onListenerDisconnected() {
        instance = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        handle(sbn, Engine.state(this))
    }

    fun onTick() {
        val st = Engine.state(this)
        if (!st.locked) return
        try {
            activeNotifications?.forEach { handle(it, st) }
        } catch (_: Exception) {
        }
    }

    private fun handle(sbn: StatusBarNotification, st: SessionState) {
        if (!st.locked || !Store.silenceNotifications(this)) return
        if (sbn.packageName !in st.packages || sbn.isOngoing) return
        val ms = st.phaseEnd - System.currentTimeMillis()
        if (ms > 500L) {
            try {
                snoozeNotification(sbn.key, ms)
            } catch (_: Exception) {
            }
        }
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }
}
