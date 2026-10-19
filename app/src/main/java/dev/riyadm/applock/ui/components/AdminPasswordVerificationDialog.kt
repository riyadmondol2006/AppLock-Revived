package dev.riyadm.applock.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import dev.riyadm.applock.R

@Composable
fun AdminPasswordVerificationDialog(
    onPasswordVerified: () -> Unit,
    onDismiss: () -> Unit,
    validatePassword: (String) -> Boolean
) {
    val context = LocalContext.current
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = {
            Text(
                text = stringResource(R.string.admin_password_verification_dialog_title),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier.padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.admin_password_verification_dialog_text),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        error = "" // Clear error when user types
                    },
                    label = { Text(stringResource(R.string.password_label)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    isError = error.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth()
                )

                if (error.isNotEmpty()) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel_button))
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = {
                        if (password.isEmpty()) {
                            error = context.getString(R.string.password_empty_error)
                        } else if (validatePassword(password)) {
                            onPasswordVerified()
                        } else {
                            error = context.getString(R.string.incorrect_password_error)
                        }
                    }
                ) {
                    Text(stringResource(R.string.verify_button))
                }
            }
        },
        dismissButton = null
    )
}
