package com.locked.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun EditorScreen(
    initial: Schedule,
    isNew: Boolean,
    onBack: () -> Unit,
    globalPkgs: Set<String>,
    onPickApps: (Schedule) -> Unit,
    onEditGlobal: (Schedule) -> Unit,
    onSave: (Schedule) -> Unit,
    onDelete: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    var name by remember { mutableStateOf(initial.name) }
    var days by remember { mutableStateOf(initial.days) }
    var start by remember { mutableStateOf(initial.startMin) }
    var end by remember { mutableStateOf(initial.endMin) }
    var picking by remember { mutableStateOf<String?>(null) }
    var useGlobal by remember { mutableStateOf(initial.useGlobal) }

    fun draft() = initial.copy(
        name = name.trim().ifEmpty { "Focus" },
        days = days, startMin = start, endMin = end,
        useGlobal = useGlobal,
    )

    val valid = days.isNotEmpty() &&
        (if (useGlobal) globalPkgs.isNotEmpty() else initial.packages.isNotEmpty())

    Scaffold(containerColor = cs.background) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            ScreenHeader(
                title = if (isNew) "New schedule" else "Edit schedule",
                onBack = onBack,
                trailing = {
                    if (!isNew) {
                        androidx.compose.material3.IconButton(onClick = onDelete) {
                            Ic(R.drawable.ic_trash, tint = cs.error)
                        }
                    }
                },
            )
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                SectionLabel("Days")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    DAY_ORDER.forEachIndexed { i, d ->
                        val on = d in days
                        Row(
                            Modifier.size(42.dp).clip(CircleShape)
                                .background(if (on) cs.primary else cs.surfaceVariant)
                                .clickable { days = if (on) days - d else days + d },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                DAY_LETTERS[i],
                                fontWeight = FontWeight.Bold,
                                color = if (on) cs.onPrimary else cs.onSurfaceVariant,
                            )
                        }
                    }
                }

                SectionLabel("Time")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TimeButton("Starts", start, Modifier.weight(1f)) { picking = "start" }
                    TimeButton("Ends", end, Modifier.weight(1f)) { picking = "end" }
                }
                if (end <= start) {
                    Text(
                        "Ends after midnight — the session runs into the next day.",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                    )
                }

                SectionLabel("Apps to lock")
                BlockListChooser(
                    useGlobal = useGlobal,
                    globalCount = globalPkgs.size,
                    customCount = initial.packages.size,
                    onUseGlobal = { useGlobal = it },
                    onEditGlobal = { onEditGlobal(draft()) },
                    onEditCustom = { onPickApps(draft()) },
                )
                Text(
                    "Each session runs 25 minutes of focus, then a 5-minute break with apps unlocked, repeating until the end time.",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                )
            }
            Button(
                onClick = { onSave(draft()) },
                enabled = valid,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Ic(R.drawable.ic_check, size = 18.dp)
                Text("  Save schedule")
            }
        }
    }

    picking?.let { which ->
        TimeDialog(
            initial = if (which == "start") start else end,
            onDismiss = { picking = null },
            onPick = { m ->
                if (which == "start") start = m else end = m
                picking = null
            },
        )
    }
}

@Composable
private fun TimeButton(label: String, minutes: Int, modifier: Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(16.dp)) {
        Ic(R.drawable.ic_clock, size = 18.dp)
        Text("  $label ${fmtMin(minutes)}")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeDialog(initial: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val state = rememberTimePickerState(initial / 60, initial % 60, true)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onPick(state.hour * 60 + state.minute) }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = { TimePicker(state = state) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickStartScreen(
    pkgs: Set<String>,
    useGlobal: Boolean,
    globalCount: Int,
    minutes: Int,
    onMinutes: (Int) -> Unit,
    onUseGlobal: (Boolean) -> Unit,
    onBack: () -> Unit,
    onPickApps: () -> Unit,
    onEditGlobal: () -> Unit,
    onStart: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Scaffold(containerColor = cs.background) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            ScreenHeader("Start now", onBack = onBack)
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                SectionLabel("Duration")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(25, 50, 90, 120).forEach { m ->
                        FilterChip(
                            selected = minutes == m,
                            onClick = { onMinutes(m) },
                            label = { Text(if (m >= 60) "${m / 60}h${if (m % 60 > 0) " ${m % 60}m" else ""}" else "${m}m") },
                        )
                    }
                }
                Text(
                    "Pomodoro still applies: 25 minutes of focus, then a 5-minute break.",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                )

                SectionLabel("Apps to lock")
                BlockListChooser(
                    useGlobal = useGlobal,
                    globalCount = globalCount,
                    customCount = pkgs.size,
                    onUseGlobal = onUseGlobal,
                    onEditGlobal = onEditGlobal,
                    onEditCustom = onPickApps,
                )
            }
            Button(
                onClick = onStart,
                enabled = if (useGlobal) globalCount > 0 else pkgs.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Ic(R.drawable.ic_play, size = 18.dp)
                Text("  Start session")
            }
        }
    }
}
