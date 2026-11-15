package com.locked.app

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(
    setup: SetupStatus,
    globalCount: Int,
    allowedCount: Int,
    onBack: () -> Unit,
    onManageGlobal: () -> Unit,
    onManageAllowed: () -> Unit,
    onFixAccessibility: () -> Unit,
    onFixNotifications: () -> Unit,
    onFixAlarms: () -> Unit,
    onFixListener: () -> Unit,
) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var tamper by remember { mutableStateOf(Store.tamperBlock(ctx)) }
    var silence by remember { mutableStateOf(Store.silenceNotifications(ctx)) }
    val exits = remember { Store.earlyExits(ctx) }

    Scaffold(containerColor = cs.background) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()),
        ) {
            ScreenHeader("Settings", onBack = onBack)
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

                SectionLabel("Protection")
                Panel(Modifier.fillMaxWidth()) {
                    Column {
                        SettingRow(
                            R.drawable.ic_shield_check, "Block Settings during focus",
                            "Stops you from switching Locked off mid-session. Settings and app installers are blocked until the break.",
                            trailing = {
                                Switch(checked = tamper, onCheckedChange = {
                                    tamper = it
                                    Store.setTamperBlock(ctx, it)
                                })
                            },
                            onClick = {
                                tamper = !tamper
                                Store.setTamperBlock(ctx, tamper)
                            },
                        )
                        SettingRow(
                            R.drawable.ic_bell_off, "Silence locked apps' notifications",
                            "Held back during focus and shown again at your break. Needs notification access.",
                            trailing = {
                                Switch(checked = silence, onCheckedChange = {
                                    silence = it
                                    Store.setSilenceNotifications(ctx, it)
                                })
                            },
                            onClick = {
                                silence = !silence
                                Store.setSilenceNotifications(ctx, silence)
                            },
                        )
                    }
                }

                SectionLabel("Block lists")
                Panel(Modifier.fillMaxWidth()) {
                    SettingRow(
                        R.drawable.ic_shield_check, "Global block list",
                        if (globalCount == 0) "Not set up yet. Build one list of distractions and reuse it across schedules."
                        else "$globalCount apps · used by every schedule set to “Global list”",
                        trailing = { Ic(R.drawable.ic_chevron_right, tint = cs.onSurfaceVariant) },
                        onClick = onManageGlobal,
                    )
                }

                SectionLabel("Always allowed")
                Panel(Modifier.fillMaxWidth()) {
                    SettingRow(
                        R.drawable.ic_layout_grid, "Allowed apps",
                        "$allowedCount apps are never locked. Phone, emergency and Locked itself are always included.",
                        trailing = { Ic(R.drawable.ic_chevron_right, tint = cs.onSurfaceVariant) },
                        onClick = onManageAllowed,
                    )
                }

                SectionLabel("Permissions")
                Panel(Modifier.fillMaxWidth()) {
                    Column {
                        PermRow(R.drawable.ic_accessibility, "Accessibility service", "Required to block apps", setup.accessibility, onFixAccessibility)
                        PermRow(R.drawable.ic_bell, "Notifications", "Countdown and alerts", setup.notifications, onFixNotifications)
                        PermRow(R.drawable.ic_alarm_clock, "Exact alarms", "On-the-second starts", setup.exactAlarms, onFixAlarms)
                        PermRow(R.drawable.ic_bell_off, "Notification access", "Optional, for silencing", setup.notificationAccess, onFixListener)
                    }
                }

                SectionLabel("Stats")
                Panel(Modifier.fillMaxWidth()) {
                    SettingRow(
                        R.drawable.ic_timer, "Early exits",
                        if (exits.isEmpty()) "None yet — nice."
                        else "${exits.size} total · last on ${fmtDate(exits.last())}",
                        trailing = {},
                        onClick = {},
                    )
                }

                Text(
                    "Locked is open source. It never reads screen content and stores everything on this device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(4.dp),
                )
                Spacer(Modifier.padding(16.dp))
            }
        }
    }
}

@Composable
private fun SettingRow(
    @DrawableRes icon: Int,
    title: String,
    subtitle: String,
    trailing: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon, cs.primary)
        Spacer(Modifier.padding(6.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
        Spacer(Modifier.padding(4.dp))
        trailing()
    }
}

@Composable
private fun PermRow(@DrawableRes icon: Int, title: String, subtitle: String, ok: Boolean, onFix: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(0.dp)).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon, if (ok) cs.secondary else cs.primary)
        Spacer(Modifier.padding(6.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
        if (ok) {
            Ic(R.drawable.ic_check, tint = cs.secondary)
        } else {
            TextButton(onClick = onFix) { Text("Grant") }
        }
    }
}
