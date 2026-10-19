package dev.riyadm.applock.features.setpassword.ui

import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import dev.riyadm.applock.AppLockApplication
import dev.riyadm.applock.R
import dev.riyadm.applock.core.navigation.Screen
import dev.riyadm.applock.core.navigation.finishPasswordSetup
import dev.riyadm.applock.core.utils.SecurityUtils
import dev.riyadm.applock.data.repository.PreferencesRepository

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AlphanumericSetPasswordScreen(
    navController: NavController,
    isFirstTimeSetup: Boolean
) {
    var passwordState by remember { mutableStateOf("") }
    var confirmPasswordState by remember { mutableStateOf("") }
    var isConfirmationMode by remember { mutableStateOf(false) }
    var isVerifyOldPasswordMode by remember { mutableStateOf(!isFirstTimeSetup) }

    var passwordVisible by remember { mutableStateOf(false) }

    var showMismatchError by remember { mutableStateOf(false) }
    var showLengthError by remember { mutableStateOf(false) }
    var showMaxLengthError by remember { mutableStateOf(false) }
    var showInvalidOldPasswordError by remember { mutableStateOf(false) }

    val minLength = 4
    val maxLength = 64
    val context = LocalContext.current
    val activity = LocalActivity.current as? ComponentActivity
    val appLockRepository = remember {
        (context.applicationContext as? AppLockApplication)?.appLockRepository
    }

    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    BackHandler {
        if (isFirstTimeSetup) {
            if (isConfirmationMode) {
                isConfirmationMode = false
            } else {
                Toast.makeText(context, R.string.set_pin_to_continue_toast, Toast.LENGTH_SHORT).show()
            }
        } else {
            if (navController.previousBackStackEntry != null) {
                navController.popBackStack()
            } else {
                activity?.finish()
            }
        }
    }

    val fragmentActivity = LocalActivity.current as? androidx.fragment.app.FragmentActivity

    fun launchDeviceCredentialAuth() {
        if (fragmentActivity == null) return
        val executor = ContextCompat.getMainExecutor(context)
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(context.getString(R.string.authenticate_to_reset_pin_title))
            .setSubtitle(context.getString(R.string.use_device_pin_pattern_password_subtitle))
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()
        val biometricPrompt = BiometricPrompt(
            fragmentActivity, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    isVerifyOldPasswordMode = false
                    passwordState = ""
                    confirmPasswordState = ""
                    showInvalidOldPasswordError = false
                }
            })
        biometricPrompt.authenticate(promptInfo)
    }

    fun switchToPinMethod() {
        navController.navigate(Screen.SetPassword.route) {
            popUpTo(Screen.SetPasswordAlphanumeric.route) { inclusive = true }
        }
    }

    fun submitPassword() {
        val currentInput = if (isConfirmationMode) confirmPasswordState else passwordState

        if (currentInput.length < minLength) {
            showLengthError = true
            return
        }

        if (currentInput.length > maxLength) {
            showMaxLengthError = true
            return
        }

        when {
            isVerifyOldPasswordMode -> {
                if (appLockRepository!!.validatePassword(passwordState)) {
                    isVerifyOldPasswordMode = false
                    passwordState = ""
                    showInvalidOldPasswordError = false
                } else {
                    showInvalidOldPasswordError = true
                    passwordState = ""
                }
            }

            !isConfirmationMode -> {
                isConfirmationMode = true
                showLengthError = false
                showMaxLengthError = false
            }

            else -> {
                if (passwordState == confirmPasswordState) {
                    appLockRepository?.setLockType(PreferencesRepository.LOCK_TYPE_PASSWORD)
                    appLockRepository?.setPassword(passwordState)
                    Toast.makeText(
                        context,
                        context.getString(R.string.password_set_successfully_toast),
                        Toast.LENGTH_SHORT
                    ).show()

                    navController.finishPasswordSetup(isFirstTimeSetup)
                } else {
                    showMismatchError = true
                    confirmPasswordState = ""
                }
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when {
                            isVerifyOldPasswordMode -> stringResource(R.string.enter_current_password_label)
                            isConfirmationMode -> stringResource(R.string.confirm_alphanumeric_password_label)
                            else -> stringResource(R.string.set_alphanumeric_password_title)
                        },
                        style = MaterialTheme.typography.titleMediumEmphasized,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Text(
                text = when {
                    isVerifyOldPasswordMode -> stringResource(R.string.enter_current_password_label)
                    isConfirmationMode -> stringResource(R.string.confirm_alphanumeric_password_label)
                    else -> stringResource(R.string.create_alphanumeric_password_label)
                },
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(
                value = if (isConfirmationMode) confirmPasswordState else passwordState,
                onValueChange = { input ->
                    val sanitized = SecurityUtils.sanitizePassword(input)
                    if (isConfirmationMode) confirmPasswordState = sanitized else passwordState = sanitized
                    showMismatchError = false
                    showLengthError = false
                    showMaxLengthError = false
                    showInvalidOldPasswordError = false
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                label = { Text(stringResource(R.string.password_hint)) },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                trailingIcon = {
                    val image = if (passwordVisible)
                        Icons.Filled.Visibility
                    else Icons.Filled.VisibilityOff

                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(imageVector = image, contentDescription = null)
                    }
                },
                isError = showMismatchError || showLengthError || showMaxLengthError || showInvalidOldPasswordError,
                singleLine = true
            )

            if (showMismatchError) {
                Text(
                    text = stringResource(R.string.passwords_dont_match_error),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .align(Alignment.Start)
                )
            }

            if (showLengthError) {
                Text(
                    text = stringResource(R.string.password_too_short_error),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .align(Alignment.Start)
                )
            }

            if (showMaxLengthError) {
                Text(
                    text = stringResource(R.string.password_too_long_error),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .align(Alignment.Start)
                )
            }

            if (showInvalidOldPasswordError) {
                Text(
                    text = stringResource(R.string.incorrect_password_try_again),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .align(Alignment.Start)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = { submitPassword() },
                modifier = Modifier.fillMaxWidth(),
                shapes = ButtonDefaults.shapes()
            ) {
                Text(stringResource(R.string.next_button))
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (!isVerifyOldPasswordMode && !isConfirmationMode) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    TextButton(onClick = { switchToPinMethod() }) {
                        Text(stringResource(R.string.use_pin_instead))
                    }
                    TextButton(onClick = { navController.navigate(Screen.SetPasswordPattern.route) }) {
                        Text(stringResource(R.string.use_pattern_button))
                    }
                }
                
            }

            if (isVerifyOldPasswordMode) {
                TextButton(onClick = { launchDeviceCredentialAuth() }) {
                    Text(stringResource(R.string.reset_using_device_password_button))
                }
            }

            if (isVerifyOldPasswordMode || isConfirmationMode) {
                TextButton(
                    onClick = {
                        if (isVerifyOldPasswordMode) {
                            if (navController.previousBackStackEntry != null) {
                                navController.popBackStack()
                            } else {
                                activity?.finish()
                            }
                        } else {
                            isConfirmationMode = false
                            if (!isFirstTimeSetup) {
                                isVerifyOldPasswordMode = true
                            }
                        }
                        passwordState = ""
                        confirmPasswordState = ""
                        showMismatchError = false
                        showLengthError = false
                        showMaxLengthError = false
                        showInvalidOldPasswordError = false
                    }
                ) {
                    Text(
                        if (isVerifyOldPasswordMode) stringResource(R.string.cancel_button)
                        else stringResource(R.string.start_over_button)
                    )
                }
            }
        }
    }
}
