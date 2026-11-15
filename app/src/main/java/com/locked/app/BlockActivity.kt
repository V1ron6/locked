package com.locked.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** Shown over a locked app. Back and Home both lead home; there is no way "through" it. */
class BlockActivity : ComponentActivity() {

    private var pkg by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pkg = intent.getStringExtra("pkg") ?: ""

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = goHome()
        })

        setContent {
            LockedTheme {
                BlockScreen(
                    pkg = pkg,
                    onHome = { goHome() },
                    onEnded = { finish() },
                    onUnlocked = { finish() },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pkg = intent.getStringExtra("pkg") ?: pkg
    }

    private fun goHome() {
        startActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        finish()
    }
}

@Composable
private fun BlockScreen(pkg: String, onHome: () -> Unit, onEnded: () -> Unit, onUnlocked: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var confirmEnd by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }

    val st = remember(now) { Engine.state(ctx, now) }
    LaunchedEffect(st.locked, st.active) {
        if (!st.active || !st.locked) onUnlocked()
    }

    val name = remember(pkg) { if (pkg.isEmpty()) "This app" else appLabel(ctx, pkg) }
    val lastPhase = st.phaseEnd >= st.sessionEnd

    Box(Modifier.fillMaxSize().background(cs.background)) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier.size(96.dp).clip(CircleShape).background(cs.primary.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) { Ic(R.drawable.ic_lock, tint = cs.primary, size = 44.dp) }

            Gap(24.dp)
            Text(
                "$name is locked",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Gap(8.dp)
            Text(
                if (st.names.isEmpty()) "Focus session in progress" else st.names.joinToString(" · "),
                color = cs.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Gap(32.dp)
            Text(
                fmtCountdown(st.phaseEnd - now),
                fontSize = 64.sp,
                fontWeight = FontWeight.Light,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                if (lastPhase) "until the session ends" else "until your next break",
                color = cs.onSurfaceVariant,
            )
            Gap(4.dp)
            Text(
                "Session ends at ${fmtClock(st.sessionEnd)}",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
            )

            Gap(40.dp)
            Button(
                onClick = onHome,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
            ) {
                Ic(R.drawable.ic_arrow_left, size = 18.dp)
                Text("  Back to home screen")
            }
            Gap(8.dp)
            TextButton(onClick = { confirmEnd = true }) {
                Text("End session early", color = cs.onSurfaceVariant)
            }
        }
    }

    if (confirmEnd) {
        EndEarlyDialog(
            onDismiss = { confirmEnd = false },
            onEnded = {
                Engine.endEarly(ctx)
                confirmEnd = false
                onEnded()
            },
        )
    }
}
