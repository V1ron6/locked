package com.locked.app

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Session logic. State is always *computed* from the clock and the stored schedules,
 * never kept as a running flag, so reboots, missed alarms and clock changes can't desync it.
 * Alarms only exist to wake the app at the next boundary (to post notifications and
 * push an already-open locked app to the block screen).
 */
object Engine {
    const val FOCUS_MS = 25 * 60_000L
    const val BREAK_MS = 5 * 60_000L
    const val CYCLE_MS = FOCUS_MS + BREAK_MS
    const val ACTION_TICK = "com.locked.app.TICK"

    private const val CH_SESSION = "session"
    private const val CH_ALERTS = "alerts"
    private const val N_SESSION = 1
    private const val N_HEADS_UP = 2
    private const val N_PHASE = 3

    // ---------------------------------------------------------------- timeline

    private fun at(base: Calendar, dayOffset: Int, minuteOfDay: Int): Long {
        val c = base.clone() as Calendar
        c.add(Calendar.DAY_OF_YEAR, dayOffset)
        c.set(Calendar.HOUR_OF_DAY, minuteOfDay / 60)
        c.set(Calendar.MINUTE, minuteOfDay % 60)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    /** All occurrences that haven't ended yet within roughly the next week, plus the manual session. */
    fun slots(c: Context, now: Long): List<Slot> {
        val dismissed = Store.dismissed(c)
        val base = Calendar.getInstance().apply { timeInMillis = now }
        val out = ArrayList<Slot>()
        for (s in Store.schedules(c)) {
            if (!s.enabled || s.packages.isEmpty() || s.days.isEmpty()) continue
            for (off in -1..8) {
                val day = (base.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, off) }
                if (day.get(Calendar.DAY_OF_WEEK) !in s.days) continue
                val start = at(base, off, s.startMin)
                val end = at(base, off + if (s.overnight) 1 else 0, s.endMin)
                if (end <= now) continue
                if (end <= (dismissed[s.id] ?: 0L)) continue
                out += Slot(s.id, s.name, start, end, s.packages)
            }
        }
        Store.manualSlot(c, now)?.let { out += it }
        return out
    }

    fun nextSlot(c: Context, now: Long): Slot? =
        slots(c, now).filter { it.start > now }.minByOrNull { it.start }

    /** The pomodoro runs from the start of the session: 25 min focus, 5 min break, repeat. */
    fun state(c: Context, now: Long = System.currentTimeMillis()): SessionState {
        val active = slots(c, now).filter { it.start <= now && now < it.end }
        if (active.isEmpty()) return SessionState.IDLE

        val blocked = active.flatMap { it.packages }.toSet() - Store.allowedEffective(c)
        val start = active.minOf { it.start }
        val end = active.maxOf { it.end }
        val elapsed = now - start
        val cycle = (elapsed / CYCLE_MS).toInt()
        val inFocus = elapsed % CYCLE_MS < FOCUS_MS
        val phaseStart = start + cycle * CYCLE_MS + if (inFocus) 0L else FOCUS_MS
        val phaseEnd = minOf(phaseStart + if (inFocus) FOCUS_MS else BREAK_MS, end)

        return SessionState(
            active = true,
            locked = inFocus,
            packages = blocked,
            sessionStart = start,
            sessionEnd = end,
            phaseStart = phaseStart,
            phaseEnd = phaseEnd,
            cycle = cycle,
            names = active.map { it.name }.distinct(),
        )
    }

    /** Next moment anything changes: phase boundary, a slot ending, a slot starting, or the 1-min warning. */
    private fun nextEvent(c: Context, now: Long): Long? {
        val all = slots(c, now)
        var best = Long.MAX_VALUE
        val st = state(c, now)
        if (st.active) {
            best = minOf(best, st.phaseEnd)
            all.filter { it.start <= now }.forEach { best = minOf(best, it.end) }
        }
        all.filter { it.start > now }.forEach {
            best = minOf(best, it.start)
            if (it.start - 60_000L > now) best = minOf(best, it.start - 60_000L)
        }
        return if (best == Long.MAX_VALUE) null else best
    }

    // ---------------------------------------------------------------- actions

    fun startManual(c: Context, minutes: Int, pkgs: Set<String>) {
        val now = System.currentTimeMillis()
        Store.setQuickPkgs(c, pkgs)
        Store.setManual(c, now, now + minutes * 60_000L, pkgs)
        tick(c)
    }

    /** Ends the current session. Schedules stay enabled and run normally next time. */
    fun endEarly(c: Context) {
        val now = System.currentTimeMillis()
        slots(c, now).filter { it.start <= now && now < it.end }.forEach { s ->
            if (s.scheduleId != null) Store.dismiss(c, s.scheduleId, s.end) else Store.clearManual(c)
        }
        Store.logEarlyExit(c)
        tick(c)
    }

    // ---------------------------------------------------------------- tick

    /** Called by alarms, boot, and any time settings change. Safe to call as often as you like. */
    fun tick(context: Context) {
        val c = context.applicationContext
        ensureChannels(c)
        val now = System.currentTimeMillis()
        val st = state(c, now)
        val nm = NotificationManagerCompat.from(c)

        if (st.active) {
            nm.cancel(N_HEADS_UP)
            notify(c, N_SESSION, sessionNotification(c, st))
            if (now - st.phaseStart in 0..8_000L) notify(c, N_PHASE, phaseAlert(c, st))
        } else {
            nm.cancel(N_SESSION)
            val soon = slots(c, now).filter { it.start > now && it.start - now <= 61_000L }
                .minByOrNull { it.start }
            if (soon != null) notify(c, N_HEADS_UP, headsUp(c, soon)) else nm.cancel(N_HEADS_UP)
        }

        scheduleAlarm(c, now)
        LockAccessibilityService.instance?.onTick()
        LockNotificationListener.instance?.onTick()
    }

    private fun scheduleAlarm(c: Context, now: Long) {
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getBroadcast(
            c, 0, Intent(c, AlarmReceiver::class.java).setAction(ACTION_TICK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val next = nextEvent(c, now)
        if (next == null) {
            am.cancel(pi)
            return
        }
        val whenMs = next + 300L
        try {
            if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMs, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMs, pi)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMs, pi)
        }
    }

    // ---------------------------------------------------------------- notifications

    private fun ensureChannels(c: Context) {
        val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CH_SESSION, "Session timer", NotificationManager.IMPORTANCE_LOW)
                .apply { description = "Ongoing countdown while a focus session is running" }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_ALERTS, "Session alerts", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Break, focus and upcoming-session alerts" }
        )
    }

    @SuppressLint("MissingPermission")
    private fun notify(c: Context, id: Int, n: Notification) {
        try {
            NotificationManagerCompat.from(c).notify(id, n)
        } catch (_: SecurityException) {
        }
    }

    private fun openApp(c: Context): PendingIntent = PendingIntent.getActivity(
        c, 0, Intent(c, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun clock(ms: Long): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ms))

    private fun sessionNotification(c: Context, st: SessionState): Notification =
        NotificationCompat.Builder(c, CH_SESSION)
            .setSmallIcon(if (st.locked) R.drawable.ic_lock else R.drawable.ic_coffee)
            .setContentTitle(if (st.locked) "Focus · ${st.packages.size} apps locked" else "Break · apps unlocked")
            .setContentText("Session ends at ${clock(st.sessionEnd)}")
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setWhen(st.phaseEnd)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(openApp(c))
            .build()

    private fun phaseAlert(c: Context, st: SessionState): Notification {
        val (title, text) = when {
            st.locked && st.phaseStart == st.sessionStart ->
                "Focus session started" to "${st.packages.size} apps locked until ${clock(st.phaseEnd)}"
            st.locked -> "Back to focus" to "Apps locked again until ${clock(st.phaseEnd)}"
            else -> "Break time" to "Apps unlocked until ${clock(st.phaseEnd)}"
        }
        return NotificationCompat.Builder(c, CH_ALERTS)
            .setSmallIcon(if (st.locked) R.drawable.ic_lock else R.drawable.ic_coffee)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setTimeoutAfter(60_000L)
            .setContentIntent(openApp(c))
            .build()
    }

    private fun headsUp(c: Context, s: Slot): Notification =
        NotificationCompat.Builder(c, CH_SESSION)
            .setSmallIcon(R.drawable.ic_timer)
            .setContentTitle("${s.name} starts in 1 minute")
            .setContentText("${s.packages.size} apps will be locked until ${clock(s.end)}")
            .setAutoCancel(true)
            .setTimeoutAfter(90_000L)
            .setContentIntent(openApp(c))
            .build()
}
