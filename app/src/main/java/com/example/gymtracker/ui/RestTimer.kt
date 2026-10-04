package com.example.gymtracker.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun RestTimerDialog(
    totalSeconds: Int,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var remaining by remember { mutableStateOf(totalSeconds) }

    LaunchedEffect(totalSeconds) {
        while (remaining > 0) {
            delay(1000)
            remaining--
        }
        vibrate(context)
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rest") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "%02d:%02d".format(remaining / 60, remaining % 60),
                    style = MaterialTheme.typography.displayMedium
                )
                LinearProgressIndicator(
                    progress = { remaining.toFloat() / totalSeconds },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { remaining = (remaining + 30).coerceAtMost(600) }) {
                        Text("+30s")
                    }
                    OutlinedButton(onClick = { remaining = (remaining - 30).coerceAtLeast(0) }) {
                        Text("-30s")
                    }
                    OutlinedButton(onClick = { remaining = 0 }) {
                        Text("Skip")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Stop") }
        }
    )
}

private fun vibrate(context: Context) {
    val v = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        v.vibrate(VibrationEffect.createOneShot(600, VibrationEffect.DEFAULT_AMPLITUDE))
    } else {
        @Suppress("DEPRECATION") v.vibrate(600)
    }
}
