package dev.riyadm.applock.features.lockscreen.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import dev.riyadm.applock.R
import dev.riyadm.applock.core.utils.SecurityUtils
import dev.riyadm.applock.data.repository.UnlockLogRepository

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AlphanumericPasswordOverlayScreen(spec: LockScreenSpec, modifier: Modifier = Modifier) {
    val controller = rememberUnlockController(spec.packageName, UnlockLogRepository.Method.PASSWORD)
    var password by remember(spec.sessionKey) { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var showError by remember { mutableStateOf(false) }
    val shake = remember { Animatable(0f) }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
        keyboard?.show()
    }
    LaunchedEffect(controller.failureCount) {
        if (controller.failureCount == 0) return@LaunchedEffect
        showError = true
        password = ""
        shake.shake()
    }

    val enabled = controller.canInput
    val submit = {
        if (enabled && password.length >= MIN_PIN_LENGTH) controller.submit(password)
        else if (enabled) controller.haptics.reject()
    }
    val hint = spec.subtitle ?: stringResource(R.string.lock_enter_password)
    val wrong = stringResource(R.string.lock_wrong_password)

    LockScreenScaffold(
        spec = spec,
        controller = controller,
        modifier = modifier,
        status = { compact ->
            if (controller.isLockedOut) {
                LockoutIndicator(controller, compact, Modifier.padding(top = 8.dp))
            } else {
                LockStatusText(text = if (showError) wrong else hint, isError = showError)
            }
        },
        input = {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                OutlinedTextField(
                    value = password,
                    onValueChange = { input ->
                        password = SecurityUtils.sanitizePassword(input)
                        showError = false
                    },
                    enabled = enabled,
                    singleLine = true,
                    isError = showError,
                    shape = MaterialTheme.shapes.large,
                    label = { Text(stringResource(R.string.password_hint)) },
                    leadingIcon = { Icon(Icons.Rounded.Key, contentDescription = null) },
                    trailingIcon = {
                        IconButton(
                            onClick = { passwordVisible = !passwordVisible },
                            shapes = IconButtonDefaults.shapes()
                        ) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Rounded.VisibilityOff
                                else Icons.Rounded.Visibility,
                                contentDescription = stringResource(
                                    if (passwordVisible) R.string.lock_hide_password_cd
                                    else R.string.lock_show_password_cd
                                )
                            )
                        }
                    },
                    visualTransformation = if (passwordVisible) VisualTransformation.None
                    else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                        autoCorrectEnabled = false
                    ),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .shakeOffset(shake)
                        .focusRequester(focusRequester)
                )

                Spacer(Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (spec.showBiometric) {
                        FilledTonalIconButton(
                            onClick = spec.onBiometric,
                            shapes = IconButtonDefaults.shapes(),
                            modifier = Modifier.size(ButtonDefaults.MediumContainerHeight)
                        ) {
                            Icon(
                                Icons.Rounded.Fingerprint,
                                contentDescription = stringResource(R.string.lock_use_biometrics)
                            )
                        }
                    }
                    Button(
                        onClick = submit,
                        enabled = enabled || controller.isVerifying,
                        shapes = ButtonDefaults.shapes(),
                        contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight),
                        modifier = Modifier
                            .weight(1f)
                            .height(ButtonDefaults.MediumContainerHeight)
                    ) {
                        if (controller.isVerifying) {
                            LoadingIndicator(
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(32.dp)
                            )
                        } else {
                            Text(
                                stringResource(R.string.lock_unlock_button),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(Modifier.width(8.dp))
                            Icon(
                                Icons.AutoMirrored.Rounded.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    )
}
