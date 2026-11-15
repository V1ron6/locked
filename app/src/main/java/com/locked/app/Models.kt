package com.locked.app

import org.json.JSONArray
import org.json.JSONObject

/** A recurring focus schedule. [days] use java.util.Calendar.DAY_OF_WEEK (1 = Sunday .. 7 = Saturday). */
data class Schedule(
    val id: String,
    val name: String,
    val days: Set<Int>,
    val startMin: Int,
    val endMin: Int,
    val packages: Set<String>,
    val enabled: Boolean = true,
) {
    /** End time at or before start time means the session runs past midnight. */
    val overnight: Boolean get() = endMin <= startMin

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("days", JSONArray().also { a -> days.forEach { a.put(it) } })
        .put("start", startMin)
        .put("end", endMin)
        .put("pkgs", JSONArray().also { a -> packages.forEach { a.put(it) } })
        .put("enabled", enabled)

    companion object {
        fun fromJson(o: JSONObject): Schedule {
            val d = o.getJSONArray("days")
            val p = o.getJSONArray("pkgs")
            return Schedule(
                id = o.getString("id"),
                name = o.optString("name", "Focus"),
                days = (0 until d.length()).map { d.getInt(it) }.toSet(),
                startMin = o.getInt("start"),
                endMin = o.getInt("end"),
                packages = (0 until p.length()).map { p.getString(it) }.toSet(),
                enabled = o.optBoolean("enabled", true),
            )
        }
    }
}

/** One concrete occurrence of a schedule (or a manual quick session) on the timeline. */
data class Slot(
    val scheduleId: String?,
    val name: String,
    val start: Long,
    val end: Long,
    val packages: Set<String>,
)

data class SessionState(
    val active: Boolean,
    /** True during the 25-minute focus phase, false during the 5-minute break. */
    val locked: Boolean,
    /** Packages that are blocked right now (already minus the always-allowed list). */
    val packages: Set<String>,
    val sessionStart: Long,
    val sessionEnd: Long,
    val phaseStart: Long,
    val phaseEnd: Long,
    val cycle: Int,
    val names: List<String>,
) {
    companion object {
        val IDLE = SessionState(false, false, emptySet(), 0L, 0L, 0L, 0L, 0, emptyList())
    }
}
