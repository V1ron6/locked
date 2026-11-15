package com.locked.app

import android.content.Context
import android.content.SharedPreferences
import android.provider.Telephony
import android.telecom.TelecomManager
import org.json.JSONArray
import org.json.JSONObject

/** Everything is kept in one SharedPreferences file; no database needed at this scale. */
object Store {
    private fun p(c: Context): SharedPreferences =
        c.applicationContext.getSharedPreferences("locked", Context.MODE_PRIVATE)

    // ---- schedules ----

    fun schedules(c: Context): List<Schedule> {
        val raw = p(c).getString("schedules", null) ?: return emptyList()
        return try {
            val a = JSONArray(raw)
            (0 until a.length()).map { Schedule.fromJson(a.getJSONObject(it)) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun save(c: Context, list: List<Schedule>) {
        val a = JSONArray()
        list.forEach { a.put(it.toJson()) }
        p(c).edit().putString("schedules", a.toString()).apply()
    }

    fun upsert(c: Context, s: Schedule) {
        val l = schedules(c).toMutableList()
        val i = l.indexOfFirst { it.id == s.id }
        if (i >= 0) l[i] = s else l.add(s)
        save(c, l)
    }

    fun delete(c: Context, id: String) = save(c, schedules(c).filter { it.id != id })

    fun setEnabled(c: Context, id: String, on: Boolean) =
        save(c, schedules(c).map { if (it.id == id) it.copy(enabled = on) else it })

    // ---- always-allowed apps ----

    fun userAllowed(c: Context): Set<String> =
        p(c).getStringSet("allowed", null)?.toSet() ?: Defaults.allowed(c)

    fun setAllowed(c: Context, s: Set<String>) {
        p(c).edit().putStringSet("allowed", s.toSet()).apply()
    }

    /** User list plus the pinned apps (dialer, emergency, Locked itself) that can never be locked. */
    fun allowedEffective(c: Context): Set<String> = userAllowed(c) + Defaults.pinned(c)

    // ---- options ----

    fun tamperBlock(c: Context) = p(c).getBoolean("tamper", false)
    fun setTamperBlock(c: Context, v: Boolean) = p(c).edit().putBoolean("tamper", v).apply()

    fun silenceNotifications(c: Context) = p(c).getBoolean("silence", true)
    fun setSilenceNotifications(c: Context, v: Boolean) = p(c).edit().putBoolean("silence", v).apply()

    // ---- manual "start now" session ----

    fun manualSlot(c: Context, now: Long): Slot? {
        val sp = p(c)
        val end = sp.getLong("m_end", 0L)
        if (end <= now) return null
        val pk = sp.getStringSet("m_pkgs", emptySet())?.toSet() ?: emptySet()
        return Slot(null, "Quick session", sp.getLong("m_start", now), end, pk)
    }

    fun setManual(c: Context, start: Long, end: Long, pkgs: Set<String>) {
        p(c).edit().putLong("m_start", start).putLong("m_end", end)
            .putStringSet("m_pkgs", pkgs.toSet()).apply()
    }

    fun clearManual(c: Context) {
        p(c).edit().putLong("m_end", 0L).apply()
    }

    fun quickPkgs(c: Context): Set<String> = p(c).getStringSet("quick_pkgs", null)?.toSet() ?: emptySet()
    fun setQuickPkgs(c: Context, s: Set<String>) = p(c).edit().putStringSet("quick_pkgs", s.toSet()).apply()

    // ---- schedule occurrences ended early (scheduleId -> slot end time) ----

    fun dismissed(c: Context): Map<String, Long> {
        return try {
            val o = JSONObject(p(c).getString("dismissed", "{}") ?: "{}")
            o.keys().asSequence().associateWith { o.getLong(it) }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun dismiss(c: Context, id: String, end: Long) {
        val cutoff = System.currentTimeMillis() - 24 * 3_600_000L
        val m = dismissed(c).toMutableMap()
        m[id] = end
        val o = JSONObject()
        m.filterValues { it > cutoff }.forEach { (k, v) -> o.put(k, v) }
        p(c).edit().putString("dismissed", o.toString()).apply()
    }

    // ---- early-exit log ----

    fun earlyExits(c: Context): List<Long> {
        return try {
            val a = JSONArray(p(c).getString("exits", "[]") ?: "[]")
            (0 until a.length()).map { a.getLong(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun logEarlyExit(c: Context) {
        val a = JSONArray()
        (earlyExits(c) + System.currentTimeMillis()).takeLast(200).forEach { a.put(it) }
        p(c).edit().putString("exits", a.toString()).apply()
    }
}

object Defaults {
    private val messaging = listOf(
        "com.google.android.apps.messaging",
        "com.samsung.android.messaging",
        "com.android.mms",
    )
    private val clocks = listOf(
        "com.google.android.deskclock",
        "com.android.deskclock",
        "com.sec.android.app.clockpackage",
    )

    /** Apps that can never be locked and cannot be removed from the allowed list. */
    fun pinned(c: Context): Set<String> {
        val out = mutableSetOf(c.packageName, "com.android.emergency")
        try {
            (c.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager)
                ?.defaultDialerPackage?.let { out += it }
        } catch (_: Exception) {
        }
        return out
    }

    fun allowed(c: Context): Set<String> {
        val out = pinned(c).toMutableSet()
        out += messaging
        out += clocks
        out += "com.google.android.dialer"
        try {
            Telephony.Sms.getDefaultSmsPackage(c)?.let { out += it }
        } catch (_: Exception) {
        }
        return out
    }
}
