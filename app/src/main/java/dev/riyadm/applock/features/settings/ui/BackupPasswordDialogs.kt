package dev.riyadm.applock.features.settings.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EnhancedEncryption
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import dev.riyadm.applock.R
import dev.riyadm.applock.data.repository.BackupCrypto
import dev.riyadm.applock.ui.components.ExpressiveShapeIcon

/** Copies the text without creating a String, so the caller can wipe it after use. */
private fun CharSequence.toCharArray(): CharArray = CharArray(length) { this[it] }

/** Asks for a password before exporting; exporting without one stays possible. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExportProtectionDialog(
    onEncrypt: (CharArray) -> Unit,
    onExportPlain: () -> Unit,
    onDismiss: () -> Unit
) {
    val password = remember { TextFieldState() }
    val confirm = remember { TextFieldState() }
    var visible by remember { mutableStateOf(false) }
    var attempted by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    val tooShort = password.text.length < BackupCrypto.MIN_PASSWORD_LENGTH
    val mismatch = !confirm.text.contentEquals(password.text)
    val valid = !tooShort && !mismatch

    fun submit() {
        attempted = true
        if (valid) {
            val chars = password.text.toCharArray()
            password.clearText()
            confirm.clearText()
            onEncrypt(chars)
        }
    }

    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnClickOutside = false),
        icon = { ExpressiveShapeIcon(icon = Icons.Rounded.EnhancedEncryption, shape = MaterialShapes.Cookie9Sided) },
        title = {
            Text(
                text = stringResource(R.string.backup_protect_title),
                style = MaterialTheme.typography.headlineSmallEmphasized,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.backup_protect_text),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                PasswordField(
                    state = password,
                    label = stringResource(R.string.backup_password_label),
                    visible = visible,
                    onToggleVisible = { visible = !visible },
                    error = if (attempted && tooShort) {
                        pluralStringResource(
                            R.plurals.backup_password_too_short,
                            BackupCrypto.MIN_PASSWORD_LENGTH,
                            BackupCrypto.MIN_PASSWORD_LENGTH
                        )
                    } else null,
                    imeAction = ImeAction.Next,
                    onImeAction = { },
                    modifier = Modifier.focusRequester(focusRequester)
                )
                PasswordField(
                    state = confirm,
                    label = stringResource(R.string.backup_password_confirm_label),
                    visible = visible,
                    onToggleVisible = { visible = !visible },
                    error = if (attempted && !tooShort && mismatch) {
                        stringResource(R.string.backup_password_mismatch)
                    } else null,
                    imeAction = ImeAction.Done,
                    onImeAction = ::submit
                )
                Text(
                    text = stringResource(R.string.backup_password_forget_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = ::submit, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.backup_encrypt_and_export))
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.cancel_button))
                }
                TextButton(onClick = onExportPlain, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.backup_export_unencrypted))
                }
            }
        }
    )
}

/** Asks for the password of an encrypted backup. [wrongPassword] shows the error and shakes. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ImportPasswordDialog(
    busy: Boolean,
    wrongPassword: Int,
    onSubmit: (CharArray) -> Unit,
    onDismiss: () -> Unit
) {
    val password = remember { TextFieldState() }
    var visible by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val shake = remember { Animatable(0f) }
    val haptics = rememberSettingsHaptics()
    val spec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()

    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
    LaunchedEffect(wrongPassword) {
        if (wrongPassword == 0) return@LaunchedEffect
        haptics.reject()
        password.clearText()
        for (target in listOf(-12f, 10f, -6f, 0f)) shake.animateTo(target, spec)
    }

    fun submit() {
        if (!busy && password.text.isNotEmpty()) onSubmit(password.text.toCharArray())
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        properties = DialogProperties(dismissOnClickOutside = false),
        icon = { ExpressiveShapeIcon(icon = Icons.Rounded.Key, shape = MaterialShapes.Cookie9Sided) },
        title = {
            Text(
                text = stringResource(R.string.backup_unlock_title),
                style = MaterialTheme.typography.headlineSmallEmphasized,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.backup_unlock_text),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                PasswordField(
                    state = password,
                    label = stringResource(R.string.backup_password_label),
                    visible = visible,
                    onToggleVisible = { visible = !visible },
                    error = if (wrongPassword > 0) stringResource(R.string.backup_wrong_password) else null,
                    imeAction = ImeAction.Done,
                    onImeAction = ::submit,
                    enabled = !busy,
                    modifier = Modifier
                        .focusRequester(focusRequester)
                        .offset { IntOffset(shake.value.dp.roundToPx(), 0) }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = ::submit,
                enabled = !busy && password.text.isNotEmpty(),
                shapes = ButtonDefaults.shapes()
            ) {
                Text(stringResource(R.string.backup_unlock_restore))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.cancel_button))
            }
        }
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PasswordField(
    state: TextFieldState,
    label: String,
    visible: Boolean,
    onToggleVisible: () -> Unit,
    error: String?,
    imeAction: ImeAction,
    onImeAction: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    OutlinedSecureTextField(
        state = state,
        enabled = enabled,
        isError = error != null,
        shape = MaterialTheme.shapes.large,
        label = { Text(label) },
        supportingText = error?.let { { Text(it) } },
        trailingIcon = {
            IconButton(onClick = onToggleVisible, shapes = IconButtonDefaults.shapes()) {
                Icon(
                    imageVector = if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    contentDescription = stringResource(
                        if (visible) R.string.backup_hide_password_cd else R.string.backup_show_password_cd
                    )
                )
            }
        },
        textObfuscationMode = if (visible) TextObfuscationMode.Visible else TextObfuscationMode.RevealLastTyped,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = imeAction,
            autoCorrectEnabled = false
        ),
        onKeyboardAction = if (imeAction == ImeAction.Done) KeyboardActionHandler { onImeAction() } else null,
        modifier = modifier.fillMaxWidth()
    )
}
