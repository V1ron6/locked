package com.locked.app

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppEntry(val pkg: String, val label: String)

@Suppress("DEPRECATION")
private fun loadApps(c: Context): List<AppEntry> {
    val pm = c.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        .map { AppEntry(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
        .filter { it.pkg != c.packageName }
        .distinctBy { it.pkg }
        .sortedBy { it.label.lowercase() }
}

/** Multi-select app list. [fixed] apps are always selected and can't be unticked. */
@Composable
fun AppPickerScreen(
    title: String,
    initial: Set<String>,
    fixed: Set<String> = emptySet(),
    onBack: () -> Unit,
    onDone: (Set<String>) -> Unit,
) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var apps by remember { mutableStateOf<List<AppEntry>?>(null) }
    var query by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf(initial + fixed) }

    LaunchedEffect(Unit) { apps = withContext(Dispatchers.Default) { loadApps(ctx) } }

    Scaffold(containerColor = cs.background) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            ScreenHeader(title, onBack = onBack)
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search apps") },
                leadingIcon = { Ic(R.drawable.ic_search, tint = cs.onSurfaceVariant, size = 20.dp) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            Text(
                "${picked.size} selected",
                style = MaterialTheme.typography.labelMedium,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )

            val list = apps
            if (list == null) {
                Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.padding(24.dp))
                    CircularProgressIndicator()
                }
            } else {
                val shown = remember(list, query) {
                    if (query.isBlank()) list else list.filter { it.label.contains(query.trim(), ignoreCase = true) }
                }
                LazyColumn(Modifier.weight(1f)) {
                    items(shown, key = { it.pkg }) { app ->
                        val locked = app.pkg in fixed
                        val on = app.pkg in picked
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable(enabled = !locked) {
                                    picked = if (on) picked - app.pkg else picked + app.pkg
                                }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AppIcon(app.pkg)
                            Spacer(Modifier.padding(6.dp))
                            Column(Modifier.weight(1f)) {
                                Text(app.label)
                                Text(
                                    if (locked) "Always allowed" else app.pkg,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = cs.onSurfaceVariant,
                                )
                            }
                            Checkbox(checked = on, onCheckedChange = null, enabled = !locked)
                        }
                    }
                }
            }

            Button(
                onClick = { onDone(picked) },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            ) {
                Ic(R.drawable.ic_check, size = 18.dp)
                Text("  Done")
            }
        }
    }
}
