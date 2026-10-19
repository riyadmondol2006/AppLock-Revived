package dev.riyadm.applock.features.lockscreen.ui

import android.content.Context
import android.content.Intent
import android.util.LruCache
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.graphics.shapes.Morph
import dev.riyadm.applock.R
import dev.riyadm.applock.core.utils.appLockRepository
import dev.riyadm.applock.data.repository.AppLockRepository
import dev.riyadm.applock.data.repository.PreferencesRepository
import dev.riyadm.applock.data.repository.UnlockLogRepository
import dev.riyadm.applock.features.lockscreen.domain.PolicyUnlockGate
import dev.riyadm.applock.ui.motion.AppHaptics
import dev.riyadm.applock.ui.motion.rememberAppHaptics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.concurrent.thread
import kotlin.math.max

/** Everything a lock screen needs from its host. */
@Immutable
class LockScreenSpec(
    val packageName: String,
    val title: String,
    val subtitle: String? = null,
    val fallbackIcon: ImageVector? = null,
    val showBiometric: Boolean = false,
    val onBiometric: () -> Unit = {},
    val onClose: (() -> Unit)? = null,
    val closeIcon: ImageVector = Icons.Rounded.Close,
    val closeContentDescription: String? = null,
    val sessionKey: Any = Unit,
    /** Per-app security level for this session; null keeps the plain credential + biometric flow. */
    val gate: PolicyUnlockGate? = null,
    val onSuccess: () -> Unit,
)

/** Whether the credential input may be used right now (hidden until biometrics pass or fail hard). */
internal val LockScreenSpec.credentialVisible: Boolean
    get() = gate?.showCredentialInput ?: true

internal val LockScreenSpec.biometricVisible: Boolean
    get() = showBiometric && (gate?.showBiometricButton ?: true)

/** The hint under the title, following the app's security level. */
@Composable
internal fun policyHint(spec: LockScreenSpec, credentialHint: String, nextStepHint: Int): String {
    spec.subtitle?.let { return it }
    val gate = spec.gate ?: return credentialHint
    return when {
        gate.awaitingCredential -> stringResource(nextStepHint)
        !gate.showCredentialInput -> stringResource(
            if (gate.policy.requireBoth) R.string.policy_lock_step_biometric
            else R.string.policy_lock_biometric_required
        )
        else -> credentialHint
    }
}

/**
 * Shown instead of the credential input while the policy asks for biometrics first. During a
 * two-step lockout only the countdown is shown; the biometric step comes back once it ends.
 */
@Composable
internal fun BiometricFirstPanel(
    spec: LockScreenSpec,
    controller: UnlockController,
    modifier: Modifier = Modifier
) {
    val blocked = controller.isLockedOut && spec.gate?.biometricWaitsForLockout == true
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (spec.showBiometric && !blocked) BiometricButton(onClick = spec.onBiometric, compact = false)
    }
}

/** Picks the screen for the configured credential type. */
@Composable
fun LockScreen(spec: LockScreenSpec, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lockType = remember(spec.sessionKey) { context.appLockRepository().getLockType() }
    when (lockType) {
        PreferencesRepository.LOCK_TYPE_PATTERN -> PatternLockScreen(spec, modifier)
        PreferencesRepository.LOCK_TYPE_PASSWORD -> AlphanumericPasswordOverlayScreen(spec, modifier)
        else -> PinPasswordOverlayScreen(spec, modifier)
    }
}

/**
 * Validation, brute-force lockout and success state for one lock screen session.
 * Credential checks (PBKDF2) always run on [Dispatchers.Default].
 */
@Stable
class UnlockController internal constructor(
    private val repository: AppLockRepository,
    private val scope: CoroutineScope,
    val haptics: AppHaptics,
    private val method: UnlockLogRepository.Method,
    private val packageName: () -> String,
) {
    var isVerifying by mutableStateOf(false)
        private set
    var isUnlocked by mutableStateOf(false)
        private set
    var remainingLockoutMs by mutableLongStateOf(0L)
        private set
    var lockoutEndsAt by mutableLongStateOf(0L)
        private set
    var lockoutTotalMs by mutableLongStateOf(0L)
        private set
    var failureCount by mutableIntStateOf(0)
        private set

    // A silently checked (auto-unlock) candidate that turned out wrong; it counts as a
    // failed attempt once the user abandons it, so auto-unlock can't be used to brute force.
    private var pendingSilentFailure: String? = null
    private var lastCountedSilentFailure: String? = null
    private val silentCheck = Mutex()

    private val lockedOutState = derivedStateOf { remainingLockoutMs > 0 }
    val isLockedOut: Boolean get() = lockedOutState.value
    val canInput: Boolean get() = !isVerifying && !isUnlocked && !isLockedOut

    fun refreshLockout() {
        val remaining = repository.getRemainingLockoutMs()
        if (remaining <= 0L) {
            remainingLockoutMs = 0L
            lockoutEndsAt = 0L
            lockoutTotalMs = 0L
            return
        }
        if (remaining > lockoutTotalMs) lockoutTotalMs = remaining
        remainingLockoutMs = remaining
        lockoutEndsAt = System.currentTimeMillis() + remaining
    }

    fun submit(input: String) {
        if (!canInput || input.isEmpty()) return
        pendingSilentFailure = null
        lastCountedSilentFailure = null
        isVerifying = true
        scope.launch {
            val valid = try {
                withContext(Dispatchers.Default) {
                    validate(input).also { repository.recordUnlockAttempt(packageName(), it, method) }
                }
            } finally {
                isVerifying = false
            }
            if (valid) succeed() else fail()
        }
    }

    /**
     * Auto-unlock check: a wrong candidate isn't reported until it is abandoned.
     * Checks run one at a time; a caller cancelled while waiting (newer input) never hashes,
     * so fast typing can't pile up PBKDF2 work on the background cores.
     */
    suspend fun trySilently(input: String) {
        if (!canInput) return
        val valid = silentCheck.withLock {
            withContext(Dispatchers.Default) { validate(input) }
        }
        if (!canInput) return
        if (valid) {
            pendingSilentFailure = null
            withContext(NonCancellable + Dispatchers.Default) {
                repository.recordUnlockAttempt(packageName(), true, method)
            }
            succeed()
        } else {
            pendingSilentFailure = input
        }
    }

    /** Call whenever the typed input changes (backspace, clear, new digit). */
    fun onInputChanged(newInput: String) {
        val pending = pendingSilentFailure ?: return
        if (newInput.startsWith(pending)) return
        pendingSilentFailure = null
        // Deleting back through one wrong entry counts once, not once per digit.
        if (lastCountedSilentFailure?.startsWith(pending) == true) return
        lastCountedSilentFailure = pending
        scope.launch {
            withContext(Dispatchers.Default) {
                repository.recordUnlockAttempt(packageName(), false, method)
            }
            refreshLockout()
        }
    }

    internal fun onDispose() {
        if (pendingSilentFailure == null || isUnlocked) return
        pendingSilentFailure = null
        val pkg = packageName()
        thread(name = "unlock-attempt") { repository.recordUnlockAttempt(pkg, false, method) }
    }

    private fun validate(input: String): Boolean =
        if (method == UnlockLogRepository.Method.PATTERN) repository.validatePattern(input)
        else repository.validatePassword(input)

    private fun succeed() {
        if (isUnlocked) return
        isUnlocked = true
        haptics.confirm()
    }

    private fun fail() {
        failureCount++
        haptics.reject()
        refreshLockout()
    }
}

@Composable
internal fun rememberUnlockController(
    packageName: String,
    method: UnlockLogRepository.Method
): UnlockController {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = rememberAppHaptics()
    val currentPackage by rememberUpdatedState(packageName)
    val controller = remember(method) {
        UnlockController(context.appLockRepository(), scope, haptics, method) { currentPackage }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { controller.refreshLockout() }
    LaunchedEffect(controller, controller.isLockedOut) {
        controller.refreshLockout()
        while (controller.isLockedOut) {
            delay(250)
            controller.refreshLockout()
        }
    }
    DisposableEffect(controller) { onDispose { controller.onDispose() } }
    return controller
}

/** Horizontal spring shake, in dp. Read the value in an offset/graphicsLayer lambda. */
internal suspend fun Animatable<Float, AnimationVector1D>.shake() {
    snapTo(0f)
    animateTo(0f, spring(dampingRatio = 0.28f, stiffness = 1400f), initialVelocity = -720f)
}

internal fun Modifier.shakeOffset(shake: Animatable<Float, AnimationVector1D>) =
    offset { IntOffset(shake.value.dp.roundToPx(), 0) }

/**
 * Full-screen frame shared by every lock screen: hero icon + title on top (or at the
 * start in landscape), the credential input below (or at the end). Handles insets,
 * the close button and the success scale-out.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun LockScreenScaffold(
    spec: LockScreenSpec,
    controller: UnlockController,
    modifier: Modifier = Modifier,
    status: @Composable ColumnScope.(compact: Boolean) -> Unit,
    input: @Composable BoxScope.(compact: Boolean) -> Unit,
) {
    val exit = remember { Animatable(0f) }
    val onSuccess by rememberUpdatedState(spec.onSuccess)
    val exitSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    LaunchedEffect(controller.isUnlocked) {
        if (controller.isUnlocked) {
            // The credential only completes the unlock when the app's policy is satisfied.
            if (spec.gate?.onCredentialSucceeded() == false) return@LaunchedEffect
            withTimeoutOrNull(SUCCESS_EXIT_MS) { exit.animateTo(1f, exitSpec) }
            onSuccess()
        }
    }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                val p = exit.value
                alpha = 1f - p
                val scale = 1f + 0.08f * p
                scaleX = scale
                scaleY = scale
            },
        color = MaterialTheme.colorScheme.surface
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            val twoPane = maxWidth > maxHeight && maxWidth >= 560.dp
            val compact = twoPane || maxHeight < 600.dp

            if (twoPane) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LockHero(
                        spec = spec,
                        compact = true,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        status = status
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .widthIn(max = 440.dp),
                        contentAlignment = Alignment.Center
                    ) { input(true) }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    LockHero(
                        spec = spec,
                        compact = compact,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(top = 40.dp),
                        status = status
                    )
                    Box(
                        modifier = Modifier
                            .widthIn(max = 440.dp)
                            .fillMaxWidth()
                            .weight(if (compact) 1.1f else 1.25f)
                            .padding(bottom = 16.dp),
                        contentAlignment = Alignment.BottomCenter
                    ) { input(compact) }
                }
            }

            spec.onClose?.let { onClose ->
                IconButton(
                    onClick = onClose,
                    shapes = IconButtonDefaults.shapes(),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                ) {
                    Icon(
                        imageVector = spec.closeIcon,
                        contentDescription = spec.closeContentDescription
                            ?: stringResource(R.string.lock_close_cd)
                    )
                }
            }
        }
    }
}

@Composable
private fun LockHero(
    spec: LockScreenSpec,
    compact: Boolean,
    modifier: Modifier,
    status: @Composable ColumnScope.(compact: Boolean) -> Unit,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AppIconBadge(
            packageName = spec.packageName.takeIf { spec.fallbackIcon == null },
            fallbackIcon = spec.fallbackIcon ?: Icons.Rounded.Lock,
            size = if (compact) 72.dp else 104.dp,
            contentDescription = stringResource(R.string.lock_app_icon_cd, spec.title)
        )
        Spacer(Modifier.height(if (compact) 12.dp else 24.dp))
        Text(
            text = spec.title,
            style = if (compact) MaterialTheme.typography.titleLargeEmphasized
            else MaterialTheme.typography.headlineMediumEmphasized,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 48.dp)
        )
        Spacer(Modifier.height(if (compact) 4.dp else 8.dp))
        status(compact)
    }
}

/** Subtitle line that cross-fades/slides between the hint and an error. */
@Composable
internal fun LockStatusText(text: String, isError: Boolean, modifier: Modifier = Modifier) {
    val motion = MaterialTheme.motionScheme
    AnimatedContent(
        targetState = text to isError,
        transitionSpec = {
            (fadeIn(motion.defaultEffectsSpec()) +
                    slideInVertically(motion.fastSpatialSpec()) { it / 3 }) togetherWith
                    (fadeOut(motion.fastEffectsSpec()) +
                            slideOutVertically(motion.fastSpatialSpec()) { -it / 3 })
        },
        contentAlignment = Alignment.Center,
        modifier = modifier,
        label = "lockStatus"
    ) { (message, error) ->
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = if (error) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun BiometricButton(onClick: () -> Unit, compact: Boolean, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.lock_use_biometrics)
    if (compact) {
        FilledTonalIconButton(
            onClick = onClick,
            shapes = IconButtonDefaults.shapes(),
            modifier = modifier.size(IconButtonDefaults.mediumContainerSize())
        ) {
            Icon(Icons.Rounded.Fingerprint, contentDescription = label)
        }
    } else {
        FilledTonalButton(
            onClick = onClick,
            shapes = ButtonDefaults.shapes(),
            modifier = modifier.height(ButtonDefaults.MediumContainerHeight),
            contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight)
        ) {
            Icon(
                Icons.Rounded.Fingerprint,
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.iconSizeFor(ButtonDefaults.MediumContainerHeight))
            )
            Spacer(Modifier.width(ButtonDefaults.iconSpacingFor(ButtonDefaults.MediumContainerHeight)))
            Text(label)
        }
    }
}

/** Live lockout countdown with a draining wavy ring. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun LockoutIndicator(controller: UnlockController, compact: Boolean, modifier: Modifier = Modifier) {
    val seconds by remember(controller) {
        derivedStateOf { ((controller.remainingLockoutMs + 999) / 1000).toInt() }
    }
    val time = formatLockoutTime(seconds)
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            CircularWavyProgressIndicator(
                progress = {
                    val total = controller.lockoutTotalMs
                    if (total <= 0L) 0f
                    else ((controller.lockoutEndsAt - System.currentTimeMillis()).toFloat() / total)
                        .coerceIn(0f, 1f)
                },
                modifier = Modifier.size(if (compact) 56.dp else 72.dp),
                color = MaterialTheme.colorScheme.error,
                trackColor = MaterialTheme.colorScheme.errorContainer
            )
            Text(
                text = time,
                style = MaterialTheme.typography.labelLargeEmphasized,
                color = MaterialTheme.colorScheme.error
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.lock_lockout_message, time),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
    }
}

internal fun formatLockoutTime(totalSeconds: Int): String =
    String.format(Locale.getDefault(), "%d:%02d", totalSeconds / 60, totalSeconds % 60)

private object LockIconCache {
    private val cache = LruCache<String, ImageBitmap>(16)
    fun get(key: String): ImageBitmap? = cache.get(key)
    fun put(key: String, bitmap: ImageBitmap) {
        cache.put(key, bitmap)
    }
}

/**
 * The locked app's icon inside a slowly rotating, breathing cookie shape.
 * All animated values are read in the draw phase only.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun AppIconBadge(
    packageName: String?,
    fallbackIcon: ImageVector,
    size: Dp,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val iconPx = with(LocalDensity.current) { (size * ICON_FRACTION).roundToPx() }
    val icon by produceState(
        initialValue = packageName?.let { LockIconCache.get("$it@$iconPx") },
        packageName,
        iconPx
    ) {
        if (packageName == null || value != null) return@produceState
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.packageManager.getApplicationIcon(packageName)
                    .toBitmap(iconPx, iconPx)
                    .asImageBitmap()
                    .also { LockIconCache.put("$packageName@$iconPx", it) }
            }.getOrNull()
        }
    }

    val transition = rememberInfiniteTransition(label = "badge")
    val rotation = transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(36_000, easing = LinearEasing)),
        label = "badgeRotation"
    )
    val breath = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(3_200, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "badgeMorph"
    )
    val morph = remember { Morph(MaterialShapes.Cookie9Sided, MaterialShapes.Cookie12Sided) }
    val path = remember { Path() }
    val matrix = remember { Matrix() }
    val container = MaterialTheme.colorScheme.primaryContainer

    Box(
        modifier = modifier
            .size(size)
            .drawBehind {
                val p = breath.value
                path.rewind()
                morph.toPath(p, path)
                matrix.reset()
                val scale = 0.94f + 0.06f * p
                val w = this.size.width * scale
                val h = this.size.height * scale
                matrix.translate((this.size.width - w) / 2f, (this.size.height - h) / 2f)
                matrix.scale(w, h)
                path.transform(matrix)
                rotate(rotation.value) { drawPath(path, container) }
            },
        contentAlignment = Alignment.Center
    ) {
        val bitmap = icon
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = contentDescription,
                modifier = Modifier.size(size * ICON_FRACTION)
            )
        } else {
            Icon(
                imageVector = fallbackIcon,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(size * 0.42f)
            )
        }
    }
}

internal fun Context.canUseBiometrics(): Boolean =
    BiometricManager.from(this).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
            BiometricManager.BIOMETRIC_SUCCESS

internal fun buildBiometricPromptInfo(
    context: Context,
    appName: String?,
    credentialFallback: Boolean = true,
): BiometricPrompt.PromptInfo {
    val negative = when {
        // Biometrics come first under this app's policy: "Use PIN" would be misleading.
        !credentialFallback -> R.string.cancel_button
        else -> when (context.appLockRepository().getLockType()) {
            PreferencesRepository.LOCK_TYPE_PATTERN -> R.string.use_pattern_button
            PreferencesRepository.LOCK_TYPE_PASSWORD -> R.string.use_password_button
            else -> R.string.use_pin_button
        }
    }
    val name = appName?.takeIf { it.isNotBlank() } ?: context.getString(R.string.this_app)
    return BiometricPrompt.PromptInfo.Builder()
        .setTitle(context.getString(R.string.unlock_app_title, name))
        .setSubtitle(context.getString(R.string.lock_biometric_subtitle))
        .setNegativeButtonText(context.getString(negative))
        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
        .setConfirmationRequired(false)
        .build()
}

internal fun homeIntent(): Intent = Intent(Intent.ACTION_MAIN)
    .addCategory(Intent.CATEGORY_HOME)
    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

internal fun Context.loadAppLabel(packageName: String): String = try {
    packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
} catch (_: Exception) {
    getString(R.string.default_app_name)
}

internal const val MIN_PIN_LENGTH = 4
internal const val MAX_PIN_LENGTH = 32
private const val ICON_FRACTION = 0.6f
private const val SUCCESS_EXIT_MS = 220L
