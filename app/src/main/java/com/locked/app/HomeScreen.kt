package com.locked.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    now: Long,
    st: SessionState,
    next: Slot?,
    schedules: List<Schedule>,
    setup: SetupStatus,
    onAdd: () -> Unit,
    onEdit: (Schedule) -> Unit,
    onToggle: (Schedule, Boolean) -> Unit,
    onQuick: () -> Unit,
    onSettings: () -> Unit,
    onEndEarly: () -> Unit,
    onFixAccessibility: () -> Unit,
    onFixNotifications: () -> Unit,
    onFixAlarms: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Ic(R.drawable.ic_plus) },
                text = { Text("New schedule") },
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = pad.calculateTopPadding() + 4.dp,
                bottom = pad.calculateBottomPadding() + 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ScreenHeader(
                    title = "Locked",
                    trailing = {
                        IconButton(onClick = onSettings) { Ic(R.drawable.ic_sliders) }
                    },
                )
            }
            item { StatusCard(now, st, next, onQuick, onEndEarly) }

            if (!setup.allGood) {
                item {
                    SetupCard(setup, onFixAccessibility, onFixNotifications, onFixAlarms)
                }
            }

            item { SectionLabel("Schedules") }
            if (schedules.isEmpty()) {
                item {
                    Panel(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            IconBadge(R.drawable.ic_calendar_clock, MaterialTheme.colorScheme.primary, 56.dp)
                            Gap(12.dp)
                            Text("No schedules yet", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Create as many as you like — each one locks its own apps and starts automatically.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            } else {
                items(schedules, key = { it.id }) { s ->
                    ScheduleCard(s, onClick = { onEdit(s) }, onToggle = { onToggle(s, it) })
                }
            }
        }
    }
}

@Composable
private fun StatusCard(
    now: Long,
    st: SessionState,
    next: Slot?,
    onQuick: () -> Unit,
    onEnd: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val accent = if (st.active && !st.locked) cs.secondary else cs.primary

    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = cs.surface,
        border = BorderStroke(1.dp, if (st.active) accent.copy(alpha = 0.5f) else cs.outline),
    ) {
        Column(Modifier.padding(20.dp)) {
            if (st.active) {
                val len = (st.phaseEnd - st.phaseStart).coerceAtLeast(1L)
                val p = ((now - st.phaseStart).toFloat() / len).coerceIn(0f, 1f)
                val last = st.phaseEnd >= st.sessionEnd

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Pill(
                        if (st.locked) "FOCUS" else "BREAK",
                        if (st.locked) R.drawable.ic_lock else R.drawable.ic_coffee,
                        accent,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "Round ${st.cycle + 1}",
                        style = MaterialTheme.typography.labelLarge,
                        color = cs.onSurfaceVariant,
                    )
                }
                Gap(16.dp)
                Text(
                    fmtCountdown(st.phaseEnd - now),
                    fontSize = 56.sp,
                    fontWeight = FontWeight.Light,
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    when {
                        last -> "until the session ends"
                        st.locked -> "until your next break"
                        else -> "until focus resumes"
                    },
                    color = cs.onSurfaceVariant,
                )
                Gap(14.dp)
                LinearProgressIndicator(
                    progress = { p },
                    color = accent,
                    trackColor = cs.outline,
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                )
                Gap(12.dp)
                Text(
                    "${st.names.joinToString(" · ")} · ends ${fmtClock(st.sessionEnd)} · " +
                        "${st.packages.size} apps ${if (st.locked) "locked" else "unlocked for now"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                )
                Gap(16.dp)
                OutlinedButton(onClick = onEnd, modifier = Modifier.fillMaxWidth()) {
                    Ic(R.drawable.ic_x, size = 18.dp)
                    Text("  End session early")
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(R.drawable.ic_shield_check, cs.primary, 44.dp)
                    Spacer(Modifier.padding(6.dp))
                    Column {
                        Text("Not in a session", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (next != null) "Next: ${next.name} · ${fmtDate(next.start)} (in ${fmtIn(next.start - now)})"
                            else "No upcoming schedules",
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant,
                        )
                    }
                }
                Gap(16.dp)
                Button(onClick = onQuick, modifier = Modifier.fillMaxWidth()) {
                    Ic(R.drawable.ic_zap, size = 18.dp)
                    Text("  Start now")
                }
            }
        }
    }
}

@Composable
private fun SetupCard(
    setup: SetupStatus,
    onAccessibility: () -> Unit,
    onNotifications: () -> Unit,
    onAlarms: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Panel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Finish setup", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
            if (!setup.accessibility) {
                SetupRow(
                    R.drawable.ic_accessibility, "Turn on the accessibility service",
                    "Required. Locked needs it to see which app is open and block locked ones.",
                    onAccessibility,
                )
            }
            if (!setup.notifications) {
                SetupRow(
                    R.drawable.ic_bell, "Allow notifications",
                    "Shows the session countdown and break alerts.", onNotifications,
                )
            }
            if (!setup.exactAlarms) {
                SetupRow(
                    R.drawable.ic_alarm_clock, "Allow exact alarms",
                    "Starts sessions and breaks to the second.", onAlarms,
                )
            }
            Text(
                "On a sideloaded build, Android may grey out the accessibility toggle. Open App info → ⋮ → “Allow restricted settings” first.",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SetupRow(icon: Int, title: String, desc: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon, cs.primary)
        Spacer(Modifier.padding(6.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
        Ic(R.drawable.ic_chevron_right, tint = cs.onSurfaceVariant)
    }
}

@Composable
private fun ScheduleCard(s: Schedule, onClick: () -> Unit, onToggle: (Boolean) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Panel(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        s.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (s.enabled) cs.onSurface else cs.onSurfaceVariant,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Ic(R.drawable.ic_clock, tint = cs.onSurfaceVariant, size = 14.dp)
                        Text(
                            "  ${fmtMin(s.startMin)} – ${fmtMin(s.endMin)}" + if (s.overnight) " (+1 day)" else "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = cs.onSurfaceVariant,
                        )
                    }
                }
                Switch(checked = s.enabled, onCheckedChange = onToggle)
            }
            Gap(12.dp)
            DayDots(s.days)
            Gap(10.dp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Ic(R.drawable.ic_layout_grid, tint = cs.onSurfaceVariant, size = 14.dp)
                Text(
                    "  ${s.packages.size} apps locked",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                )
            }
        }
    }
}
