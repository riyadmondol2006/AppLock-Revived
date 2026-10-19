package dev.riyadm.applock.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

@Composable
fun UsageStatsPermission(
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        modifier = Modifier.fillMaxWidth(0.8f),
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        ),
        title = {
            Text(
                text = "Grant Usage Stats Permission",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier.padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "You've enabled Usage Stats mode, but the app requires Uage access permission to function properly.",
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Press 'Grant Permission' to open the settings and grant the necessary permissions.",
                    textAlign = TextAlign.Start,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            FilledTonalButton(onClick = onOpenSettings) {
                Text("Grant Permission")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
