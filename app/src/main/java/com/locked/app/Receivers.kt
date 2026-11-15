package com.locked.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Fires at the next session boundary. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Engine.tick(context)
    }
}

/** Reboot, app update, or clock/timezone change: recompute state and reschedule the alarm. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Engine.tick(context)
    }
}
