package com.locked.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import java.util.Calendar
import java.util.UUID

class MainActivity : ComponentActivity() {

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { Engine.tick(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Engine.tick(this)
        setContent {
            LockedTheme {
                App(onRequestNotifications = {
                    if (Build.VERSION.SDK_INT >= 33) {
                        notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                })
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Engine.tick(this)
    }
}

@Composable
private fun App(onRequestNotifications: () -> Unit) {
    val ctx = LocalContext.current

    var route by rememberSaveable { mutableStateOf("home") }
    var draft by remember { mutableStateOf<Schedule?>(null) }
    var draftIsNew by remember { mutableStateOf(true) }
    var quickPkgs by remember { mutableStateOf(Store.quickPkgs(ctx)) }
    var quickMinutes by rememberSaveable { mutableIntStateOf(50) }
    var confirmEnd by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    // Bumped after any write so reads from the store are refreshed.
    var version by remember { mutableIntStateOf(0) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }

    val schedules = remember(version) { Store.schedules(ctx) }
    val st = remember(now, version) { Engine.state(ctx, now) }
    val next = remember(now / 5000, version) { Engine.nextSlot(ctx, now) }
    val setup = remember(now) { Perms.status(ctx) }
    val allowed = remember(version) { Store.userAllowed(ctx) }

    fun changed() {
        Engine.tick(ctx)
        version++
    }

    BackHandler(enabled = route != "home") {
        route = when (route) {
            "picker_editor" -> "editor"
            "picker_quick" -> "quick"
            "picker_allowed" -> "settings"
            else -> "home"
        }
    }

    val fixAccessibility = { ctx.startActivity(Perms.accessibilityIntent()) }
    val fixAlarms = { ctx.startActivity(Perms.exactAlarmIntent(ctx)) }
    val fixListener = { ctx.startActivity(Perms.notificationAccessIntent()) }

    when (route) {
        "home" -> HomeScreen(
            now = now, st = st, next = next, schedules = schedules, setup = setup,
            onAdd = {
                draft = Schedule(
                    id = UUID.randomUUID().toString(),
                    name = "Focus",
                    days = setOf(
                        Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
                        Calendar.THURSDAY, Calendar.FRIDAY,
                    ),
                    startMin = 9 * 60, endMin = 11 * 60,
                    packages = emptySet(),
                )
                draftIsNew = true
                route = "editor"
            },
            onEdit = { draft = it; draftIsNew = false; route = "editor" },
            onToggle = { s, on -> Store.setEnabled(ctx, s.id, on); changed() },
            onQuick = {
                if (quickPkgs.isEmpty()) quickPkgs = schedules.flatMap { it.packages }.toSet()
                route = "quick"
            },
            onSettings = { route = "settings" },
            onEndEarly = { confirmEnd = true },
            onFixAccessibility = fixAccessibility,
            onFixNotifications = onRequestNotifications,
            onFixAlarms = fixAlarms,
        )

        "editor" -> draft?.let { d ->
            EditorScreen(
                initial = d,
                isNew = draftIsNew,
                onBack = { route = "home" },
                onPickApps = { edited -> draft = edited; route = "picker_editor" },
                onSave = { Store.upsert(ctx, it); changed(); route = "home" },
                onDelete = { confirmDelete = true },
            )
        }

        "picker_editor" -> draft?.let { d ->
            AppPickerScreen(
                title = "Apps to lock",
                initial = d.packages,
                onBack = { route = "editor" },
                onDone = { draft = d.copy(packages = it); route = "editor" },
            )
        }

        "quick" -> QuickStartScreen(
            pkgs = quickPkgs,
            minutes = quickMinutes,
            onMinutes = { quickMinutes = it },
            onBack = { route = "home" },
            onPickApps = { route = "picker_quick" },
            onStart = {
                Engine.startManual(ctx, quickMinutes, quickPkgs)
                version++
                route = "home"
            },
        )

        "picker_quick" -> AppPickerScreen(
            title = "Apps to lock",
            initial = quickPkgs,
            onBack = { route = "quick" },
            onDone = { quickPkgs = it; route = "quick" },
        )

        "settings" -> SettingsScreen(
            setup = setup,
            allowedCount = Store.allowedEffective(ctx).size,
            onBack = { route = "home" },
            onManageAllowed = { route = "picker_allowed" },
            onFixAccessibility = fixAccessibility,
            onFixNotifications = onRequestNotifications,
            onFixAlarms = fixAlarms,
            onFixListener = fixListener,
        )

        "picker_allowed" -> AppPickerScreen(
            title = "Always allowed",
            initial = allowed,
            fixed = Defaults.pinned(ctx),
            onBack = { route = "settings" },
            onDone = { Store.setAllowed(ctx, it); changed(); route = "settings" },
        )
    }

    if (confirmEnd) {
        EndEarlyDialog(
            onDismiss = { confirmEnd = false },
            onEnded = {
                confirmEnd = false
                Engine.endEarly(ctx)
                version++
            },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete schedule?") },
            text = { Text("This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    draft?.let { Store.delete(ctx, it.id) }
                    confirmDelete = false
                    changed()
                    route = "home"
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}
