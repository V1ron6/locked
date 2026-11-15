package com.locked.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlin.random.Random

private data class Challenge(val prompt: String, val answer: String, val numeric: Boolean) {
    companion object {
        private val phrases = listOf(
            "I am choosing distraction over my goals",
            "this can wait until my session is over",
            "I gave up on my focus early",
        )

        fun random(): Challenge =
            if (Random.nextBoolean()) {
                val a = Random.nextInt(12, 50)
                val b = Random.nextInt(3, 10)
                val c = Random.nextInt(10, 100)
                Challenge("Solve:  $a × $b + $c", (a * b + c).toString(), true)
            } else {
                val p = phrases.random()
                Challenge("Type exactly:\n“$p”", p, false)
            }
    }
}

/**
 * Two-step friction for ending a session early:
 * 1) hold a button for 3 seconds, 2) solve a random math puzzle or type a phrase.
 */
@Composable
fun EndEarlyDialog(onDismiss: () -> Unit, onEnded: () -> Unit) {
    var stage by remember { mutableIntStateOf(0) }
    val challenge = remember { Challenge.random() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (stage == 0) "End session early?" else "Prove it") },
        text = {
            Column {
                if (stage == 0) {
                    Text(
                        "Your apps unlock and the timer stops. Press and hold to continue.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Box(Modifier.height(16.dp))
                    HoldButton(onComplete = { stage = 1 })
                } else {
                    ChallengeBody(challenge, onSolved = onEnded)
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Keep focusing") } },
    )
}

@Composable
private fun HoldButton(onComplete: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val progress = remember { Animatable(0f) }
    var held by remember { mutableStateOf(false) }

    LaunchedEffect(held) {
        if (held) {
            progress.animateTo(1f, tween(3000, easing = LinearEasing))
            if (progress.value >= 1f) onComplete()
        } else {
            progress.snapTo(0f)
        }
    }

    Box(
        Modifier.fillMaxWidth().height(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(cs.surfaceVariant)
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    held = true
                    tryAwaitRelease()
                    held = false
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.align(Alignment.CenterStart).fillMaxHeight()
                .fillMaxWidth(progress.value).background(cs.primary.copy(alpha = 0.35f))
        )
        Text(if (held) "Keep holding…" else "Hold for 3 seconds", fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ChallengeBody(ch: Challenge, onSolved: () -> Unit) {
    var input by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    Column {
        Text(ch.prompt, style = MaterialTheme.typography.titleMedium)
        Box(Modifier.height(12.dp))
        OutlinedTextField(
            value = input,
            onValueChange = { input = it; wrong = false },
            singleLine = true,
            isError = wrong,
            supportingText = { if (wrong) Text("Not quite — try again") },
            keyboardOptions = KeyboardOptions(
                keyboardType = if (ch.numeric) KeyboardType.Number else KeyboardType.Text
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Box(Modifier.height(8.dp))
        Button(
            onClick = {
                if (input.trim().equals(ch.answer, ignoreCase = true)) onSolved() else wrong = true
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("End session") }
    }
}
