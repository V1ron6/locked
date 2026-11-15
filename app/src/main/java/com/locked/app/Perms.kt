package com.locked.app

import android.Manifest
import android.app.AlarmManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

data class SetupStatus(
    val accessibility: Boolean,
    val notifications: Boolean,
    val exactAlarms: Boolean,
    val notificationAccess: Boolean,
) {
    /** Required + recommended items; notification access is optional so it doesn't count. */
    val allGood: Boolean get() = accessibility && notifications && exactAlarms
}

object Perms {
    fun status(c: Context) = SetupStatus(
        accessibility = accessibilityEnabled(c),
        notifications = notificationsGranted(c),
        exactAlarms = exactAlarmsGranted(c),
        notificationAccess = NotificationManagerCompat.getEnabledListenerPackages(c).contains(c.packageName),
    )

    fun accessibilityEnabled(c: Context): Boolean {
        val me = ComponentName(c, LockAccessibilityService::class.java)
        val enabled = Settings.Secure.getString(c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    fun notificationsGranted(c: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun exactAlarmsGranted(c: Context): Boolean =
        Build.VERSION.SDK_INT < 31 ||
            (c.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()

    fun accessibilityIntent() = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun exactAlarmIntent(c: Context) = Intent(
        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${c.packageName}")
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun notificationAccessIntent() = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun appInfoIntent(c: Context) = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${c.packageName}")
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
