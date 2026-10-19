package dev.riyadm.applock.features.setpassword.ui

import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavController
import com.mrhwsn.composelock.Dot
import com.mrhwsn.composelock.LockCallback
import com.mrhwsn.composelock.PatternLock
import dev.riyadm.appintro.MorphingHero
import dev.riyadm.applock.AppLockApplication
import dev.riyadm.applock.R
import dev.riyadm.applock.core.navigation.Screen
import dev.riyadm.applock.core.navigation.finishPasswordSetup
import dev.riyadm.applock.core.utils.SecurityUtils
import dev.riyadm.applock.ui.motion.rememberAppHaptics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class SetupStep { VERIFY, CREATE, CONFIRM }

private const val MIN_LENGTH = 4
private const val MAX_PIN_LENGTH = 16
private const val MAX_PASSWORD_LENGTH = 64

/**
 * Single flow for creating or changing the app credential.
 *
 * When changing ([isFirstTimeSetup] = false) the current credential is verified first. The lock
 * type can then be switched in place, so the verified state can never be bypassed by navigating
 * to another setup route.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CredentialSetupScreen(
    navController: NavController,
    isFirstTimeSetup: Boolean,
    initialKind: LockKind
) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val repository = remember { (context.applicationContext as AppLockApplication).appLockRepository }
    val haptics = rememberAppHaptics()
    val scope = rememberCoroutineScope()

    val verifyKind = remember { LockKind.fromPref(repository.getLockType()) }
    var step by remember { mutableStateOf(if (isFirstTimeSetup) SetupStep.CREATE else SetupStep.VERIFY) }
    var kind by remember { mutableStateOf(if (isFirstTimeSetup) initialKind else verifyKind) }
    var firstEntry by remember { mutableStateOf("") }
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var verifyWithKeyboard by remember { mutableStateOf(false) }
    val shake = remember { Animatable(0f) }

    val activeKind = if (step == SetupStep.VERIFY) verifyKind else kind
    val usesTextField = activeKind == LockKind.PASSWORD ||
            (step == SetupStep.VERIFY && activeKind == LockKind.PIN && verifyWithKeyboard)

    fun fail(message: String) {
        error = message
        haptics.reject()
        scope.launch { shake.shake() }
    }

    fun resetToCreate() {
        step = SetupStep.CREATE
        firstEntry = ""
        input = ""
        error = null
    }

    fun leave() {
        if (navController.previousBackStackEntry != null) navController.popBackStack()
        else activity?.finish()
    }

    fun complete() {
        val chosen = kind
        val credential = firstEntry
        busy = true
        scope.launch {
            withContext(Dispatchers.Default) {
                repository.setLockType(chosen.prefValue)
                if (chosen == LockKind.PATTERN) repository.setPattern(credential)
                else repository.setPassword(credential)
            }
            haptics.confirm()
            Toast.makeText(
                context,
                context.getString(R.string.password_set_successfully_toast),
                Toast.LENGTH_SHORT
            ).show()
            if (isFirstTimeSetup) {
                navController.navigate(Screen.Main.route) {
                    popUpTo(navController.graph.id) { inclusive = true }
                    launchSingleTop = true
                }
            } else {
                navController.finishPasswordSetup(false)
            }
        }
    }

    fun submit(value: String) {
        if (busy) return
        when (step) {
            SetupStep.VERIFY -> {
                if (value.isEmpty()) return
                busy = true
                scope.launch {
                    val valid = withContext(Dispatchers.Default) {
                        if (verifyKind == LockKind.PATTERN) repository.validatePattern(value)
                        else repository.validatePassword(value)
                    }
                    busy = false
                    input = ""
                    if (valid) {
                        haptics.confirm()
                        step = SetupStep.CREATE
                        error = null
                    } else {
                        fail(
                            context.getString(
                                when (verifyKind) {
                                    LockKind.PIN -> R.string.incorrect_pin_try_again
                                    LockKind.PATTERN -> R.string.incorrect_pattern_try_again
                                    LockKind.PASSWORD -> R.string.incorrect_password_try_again
                                }
                            )
                        )
                    }
                }
            }

            SetupStep.CREATE -> {
                val tooShort = value.length < MIN_LENGTH
                val tooLong = kind == LockKind.PASSWORD && value.length > MAX_PASSWORD_LENGTH
                when {
                    tooShort -> fail(
                        context.getString(
                            when (kind) {
                                LockKind.PIN -> R.string.pin_min_length_error
                                LockKind.PATTERN -> R.string.setup_pattern_too_short
                                LockKind.PASSWORD -> R.string.password_too_short_error
                            }
                        )
                    )

                    tooLong -> fail(context.getString(R.string.password_too_long_error))
                    else -> {
                        haptics.tick()
                        firstEntry = value
                        input = ""
                        error = null
                        step = SetupStep.CONFIRM
                    }
                }
            }

            SetupStep.CONFIRM -> {
                if (value == firstEntry) {
                    complete()
                } else {
                    input = ""
                    fail(
                        context.getString(
                            when (kind) {
                                LockKind.PIN -> R.string.pins_dont_match_error
                                LockKind.PATTERN -> R.string.setup_patterns_dont_match
                                LockKind.PASSWORD -> R.string.passwords_dont_match_error
                            }
                        )
                    )
                }
            }
        }
    }

    fun launchDeviceCredentialAuth() {
        val fragmentActivity = activity as? FragmentActivity ?: return
        val authenticators = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        } else {
            BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        }
        try {
            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle(context.getString(R.string.authenticate_to_reset_pin_title))
                .setSubtitle(context.getString(R.string.use_device_pin_pattern_password_subtitle))
                .setAllowedAuthenticators(authenticators)
                .build()
            BiometricPrompt(
                fragmentActivity,
                ContextCompat.getMainExecutor(context),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        haptics.confirm()
                        resetToCreate()
                    }
                }
            ).authenticate(promptInfo)
        } catch (e: Exception) {
            Log.e("CredentialSetup", "Device credential prompt failed", e)
        }
    }

    BackHandler {
        when {
            step == SetupStep.CONFIRM -> resetToCreate()
            isFirstTimeSetup -> Toast.makeText(
                context, R.string.set_pin_to_continue_toast, Toast.LENGTH_SHORT
            ).show()

            else -> leave()
        }
    }

    val title = stringResource(
        when (step) {
            SetupStep.VERIFY -> when (verifyKind) {
                LockKind.PIN -> R.string.setup_verify_pin
                LockKind.PATTERN -> R.string.setup_verify_pattern
                LockKind.PASSWORD -> R.string.setup_verify_password
            }

            SetupStep.CREATE -> when (kind) {
                LockKind.PIN -> R.string.setup_create_pin
                LockKind.PATTERN -> R.string.setup_create_pattern
                LockKind.PASSWORD -> R.string.setup_create_password
            }

            SetupStep.CONFIRM -> when (kind) {
                LockKind.PIN -> R.string.setup_confirm_pin
                LockKind.PATTERN -> R.string.setup_confirm_pattern
                LockKind.PASSWORD -> R.string.setup_confirm_password
            }
        }
    )
    val hint = stringResource(
        when (step) {
            SetupStep.VERIFY -> R.string.setup_hint_verify
            SetupStep.CONFIRM -> R.string.setup_hint_confirm
            SetupStep.CREATE -> when (kind) {
                LockKind.PIN -> R.string.setup_hint_pin
                LockKind.PATTERN -> R.string.setup_hint_pattern
                LockKind.PASSWORD -> R.string.setup_hint_password
            }
        }
    )

    val header: @Composable ColumnScope.() -> Unit = {
        SetupHeader(
            step = step,
            kind = activeKind,
            title = title,
            message = error ?: hint,
            isError = error != null,
            shakeOffset = { shake.value }
        )
        AnimatedVisibility(
            visible = step == SetupStep.CREATE,
            enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                    expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()),
            exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
                    shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec())
        ) {
            LockTypeSwitcher(
                selected = kind,
                onSelect = {
                    if (it != kind) {
                        haptics.toggle(true)
                        kind = it
                        input = ""
                        error = null
                    }
                },
                modifier = Modifier
                    .padding(top = 20.dp)
                    .widthIn(max = 420.dp)
            )
        }
        if (activeKind == LockKind.PIN && !usesTextField) {
            PinDots(
                length = input.length,
                isError = error != null,
                placeholders = if (step == SetupStep.VERIFY) 0 else MIN_LENGTH,
                modifier = Modifier
                    .padding(top = 24.dp)
                    .offset { IntOffset(shake.value.dp.roundToPx(), 0) }
            )
        }
    }

    val actions: @Composable ColumnScope.() -> Unit = {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (step == SetupStep.VERIFY) {
                TextButton(onClick = { launchDeviceCredentialAuth() }, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.reset_using_device_password_button))
                }
                if (verifyKind == LockKind.PIN) {
                    TextButton(
                        onClick = {
                            verifyWithKeyboard = !verifyWithKeyboard
                            input = ""
                            error = null
                        },
                        shapes = ButtonDefaults.shapes()
                    ) {
                        Icon(
                            if (verifyWithKeyboard) Icons.Rounded.Dialpad else Icons.Rounded.Keyboard,
                            contentDescription = null,
                            modifier = Modifier.padding(end = ButtonDefaults.IconSpacing)
                        )
                        Text(
                            stringResource(
                                if (verifyWithKeyboard) R.string.setup_use_keypad else R.string.setup_use_keyboard
                            )
                        )
                    }
                }
            }
            if (step == SetupStep.CONFIRM) {
                TextButton(onClick = { resetToCreate() }, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.start_over_button))
                }
            }
        }
    }

    val inputArea: @Composable (Modifier) -> Unit = { modifier ->
        when {
            usesTextField -> PasswordInput(
                value = input,
                onValueChange = {
                    input = SecurityUtils.sanitizePassword(it)
                    error = null
                },
                onSubmit = { submit(input) },
                isError = error != null,
                isFinalStep = step == SetupStep.CONFIRM,
                keyboardType = if (activeKind == LockKind.PIN) KeyboardType.NumberPassword else KeyboardType.Password,
                focusKey = step to activeKind,
                enabled = !busy,
                modifier = modifier.offset { IntOffset(shake.value.dp.roundToPx(), 0) }
            )

            activeKind == LockKind.PATTERN -> PatternInput(
                isError = error != null,
                enabled = !busy,
                onStart = {
                    haptics.tick()
                    error = null
                },
                onDot = { haptics.tick() },
                onResult = { submit(it) },
                modifier = modifier.offset { IntOffset(shake.value.dp.roundToPx(), 0) }
            )

            else -> ExpressivePinKeypad(
                onDigit = { digit ->
                    if (input.length < MAX_PIN_LENGTH) {
                        input += digit
                        error = null
                    }
                },
                onBackspace = {
                    input = input.dropLast(1)
                    error = null
                },
                onClear = {
                    input = ""
                    error = null
                },
                onConfirm = {
                    if (step == SetupStep.VERIFY || input.length >= MIN_LENGTH) haptics.press()
                    submit(input)
                },
                confirmEnabled = if (step == SetupStep.VERIFY) input.isNotEmpty() else input.length >= MIN_LENGTH,
                isFinalStep = step != SetupStep.CREATE,
                haptics = haptics,
                enabled = !busy,
                modifier = modifier
            )
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(if (isFirstTimeSetup) R.string.setup_title_new else R.string.setup_title_change),
                        style = MaterialTheme.typography.titleLargeEmphasized
                    )
                },
                navigationIcon = {
                    if (!isFirstTimeSetup) {
                        IconButton(onClick = { leave() }, shapes = IconButtonDefaults.shapes()) {
                            Icon(
                                Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.intro_back)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
        ) {
            val landscape = maxWidth > maxHeight && maxHeight < 520.dp
            if (landscape) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        header()
                        Spacer(Modifier.height(12.dp))
                        actions()
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        inputArea(Modifier.fillMaxHeight())
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    header()
                    Spacer(Modifier.weight(1f).heightIn(min = 16.dp))
                    inputArea(
                        Modifier
                            .fillMaxWidth()
                            .widthIn(max = 420.dp)
                            .heightIn(max = 420.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    actions()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SetupHeader(
    step: SetupStep,
    kind: LockKind,
    title: String,
    message: String,
    isError: Boolean,
    shakeOffset: () -> Float
) {
    val colors = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val heroShape = when {
        isError -> MaterialShapes.SoftBurst
        step == SetupStep.VERIFY -> MaterialShapes.Cookie4Sided
        step == SetupStep.CONFIRM -> MaterialShapes.Clover8Leaf
        else -> MaterialShapes.Cookie9Sided
    }
    MorphingHero(
        shape = heroShape,
        icon = if (step == SetupStep.VERIFY) Icons.Rounded.Lock else kind.icon,
        containerColor = when {
            isError -> colors.errorContainer
            step == SetupStep.CONFIRM -> colors.tertiaryContainer
            else -> colors.primaryContainer
        },
        contentColor = when {
            isError -> colors.onErrorContainer
            step == SetupStep.CONFIRM -> colors.onTertiaryContainer
            else -> colors.onPrimaryContainer
        },
        size = 88.dp,
        modifier = Modifier.padding(top = 8.dp)
    )
    Column(
        modifier = Modifier
            .padding(top = 20.dp)
            .offset { IntOffset(shakeOffset().dp.roundToPx(), 0) },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AnimatedContent(
            targetState = title,
            transitionSpec = {
                (slideInVertically(motion.defaultSpatialSpec()) { it / 2 } + fadeIn(motion.defaultEffectsSpec()))
                    .togetherWith(slideOutVertically(motion.fastSpatialSpec()) { -it / 2 } + fadeOut(motion.fastEffectsSpec()))
            },
            label = "setup title"
        ) { text ->
            Text(
                text = text,
                style = MaterialTheme.typography.headlineMediumEmphasized,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() }
            )
        }
        val messageColor by animateColorAsState(
            if (isError) colors.error else colors.onSurfaceVariant,
            motion.defaultEffectsSpec(),
            label = "message color"
        )
        AnimatedContent(
            targetState = message,
            transitionSpec = { fadeIn(motion.defaultEffectsSpec()).togetherWith(fadeOut(motion.fastEffectsSpec())) },
            label = "setup message"
        ) { text ->
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = messageColor,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite }
            )
        }
    }
}

@Composable
private fun PatternInput(
    isError: Boolean,
    enabled: Boolean,
    onStart: () -> Unit,
    onDot: () -> Unit,
    onResult: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val currentOnStart by rememberUpdatedState(onStart)
    val currentOnDot by rememberUpdatedState(onDot)
    val currentOnResult by rememberUpdatedState(onResult)
    val currentEnabled by rememberUpdatedState(enabled)
    val dotColor by animateColorAsState(
        if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "pattern dots"
    )
    val lineColor = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val side = min(maxWidth, if (maxHeight.value.isFinite()) maxHeight else maxWidth)
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier
                .widthIn(max = side)
                .aspectRatio(1f)
        ) {
            PatternLock(
                modifier = Modifier.fillMaxSize(),
                dimension = 3,
                sensitivity = with(density) { 40.dp.toPx() },
                dotsColor = dotColor,
                dotsSize = with(density) { 7.dp.toPx() },
                linesColor = lineColor.copy(alpha = 0.8f),
                linesStroke = with(density) { 6.dp.toPx() },
                animationDuration = 120,
                callback = object : LockCallback {
                    override fun onStart(dot: Dot) = currentOnStart()
                    override fun onDotConnected(dot: Dot) = currentOnDot()
                    override fun onResult(result: List<Dot>) {
                        if (!currentEnabled) return
                        currentOnResult(result.joinToString("") { it.id.toString() })
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PasswordInput(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    isError: Boolean,
    isFinalStep: Boolean,
    keyboardType: KeyboardType,
    focusKey: Any,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    var visible by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(focusKey) {
        runCatching { focusRequester.requestFocus() }
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            label = { Text(stringResource(R.string.password_hint)) },
            singleLine = true,
            isError = isError,
            enabled = enabled,
            shape = MaterialTheme.shapes.large,
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
            trailingIcon = {
                IconButton(onClick = { visible = !visible }, shapes = IconButtonDefaults.shapes()) {
                    Icon(
                        if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = stringResource(
                            if (visible) R.string.setup_hide_password else R.string.setup_show_password
                        )
                    )
                }
            }
        )
        Button(
            onClick = onSubmit,
            enabled = enabled && value.isNotEmpty(),
            shapes = ButtonDefaults.shapes(),
            contentPadding = ButtonDefaults.MediumContentPadding,
            modifier = Modifier
                .fillMaxWidth()
                .height(ButtonDefaults.MediumContainerHeight)
        ) {
            Text(
                stringResource(if (isFinalStep) R.string.setup_confirm else R.string.setup_continue),
                style = MaterialTheme.typography.titleMediumEmphasized
            )
        }
    }
}
