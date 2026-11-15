package com.locked.app

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * "Which apps get locked?" Two choices:
 *  - Global list: one list you maintain once and reuse across schedules.
 *  - Custom list: apps picked just for this schedule / session.
 * Shared by the schedule editor and the quick-start screen.
 */
@Composable
fun BlockListChooser(
    useGlobal: Boolean,
    globalCount: Int,
    customCount: Int,
    onUseGlobal: (Boolean) -> Unit,
    onEditGlobal: () -> Unit,
    onEditCustom: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ChoiceCard(
                selected = useGlobal,
                icon = R.drawable.ic_shield_check,
                title = "Global list",
                subtitle = if (globalCount == 0) "Empty" else "$globalCount apps",
                modifier = Modifier.weight(1f),
            ) { onUseGlobal(true) }
            ChoiceCard(
                selected = !useGlobal,
                icon = R.drawable.ic_layout_grid,
                title = "Custom",
                subtitle = if (customCount == 0) "None picked" else "$customCount apps",
                modifier = Modifier.weight(1f),
            ) { onUseGlobal(false) }
        }

        val empty = if (useGlobal) globalCount == 0 else customCount == 0
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                .background(cs.surface)
                .clickable(onClick = if (useGlobal) onEditGlobal else onEditCustom)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge(R.drawable.ic_layout_grid, if (empty) cs.error else cs.primary)
            Spacer(Modifier.padding(6.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (useGlobal) "Edit global list" else "Choose apps for this schedule",
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    when {
                        useGlobal && empty -> "Your global list is empty — add apps first"
                        useGlobal -> "$globalCount apps · changes apply to every schedule using it"
                        empty -> "None selected"
                        else -> "$customCount selected · only used by this one"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (empty) cs.error else cs.onSurfaceVariant,
                )
            }
            Ic(R.drawable.ic_chevron_right, tint = cs.onSurfaceVariant)
        }
    }
}

@Composable
private fun ChoiceCard(
    selected: Boolean,
    @DrawableRes icon: Int,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = if (selected) cs.primaryContainer else cs.surface,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) cs.primary else cs.outline),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Ic(icon, tint = if (selected) cs.primary else cs.onSurfaceVariant, size = 20.dp)
                Spacer(Modifier.weight(1f))
                if (selected) Ic(R.drawable.ic_check, tint = cs.primary, size = 18.dp)
            }
            Gap(8.dp)
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
    }
}
