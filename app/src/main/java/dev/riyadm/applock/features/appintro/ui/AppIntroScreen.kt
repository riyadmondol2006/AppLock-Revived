package dev.riyadm.applock.features.appintro.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.PermissionChecker
import androidx.core.content.PermissionChecker.checkSelfPermission
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.navigation.NavController
import dev.riyadm.appintro.AppIntro
import dev.riyadm.appintro.IntroPage
import dev.riyadm.applock.R
import dev.riyadm.applock.core.navigation.Screen
import dev.riyadm.applock.core.utils.appLockRepository
import dev.riyadm.applock.core.utils.hasUsagePermission
import dev.riyadm.applock.core.utils.isAccessibilityServiceEnabled
import dev.riyadm.applock.core.utils.launchBatterySettings
import dev.riyadm.applock.core.utils.openAppInfoSettings
import dev.riyadm.applock.data.repository.BackendImplementation
import dev.riyadm.applock.features.appintro.domain.AppIntroManager
import dev.riyadm.applock.services.ShizukuAppLockService
import dev.riyadm.applock.services.UsageLockService
import dev.riyadm.applock.ui.components.ExpressiveShapeIcon
import dev.riyadm.applock.ui.components.RestrictedSettingsHint
import dev.riyadm.applock.ui.icons.Accessibility
import dev.riyadm.applock.ui.icons.BatterySaver
import dev.riyadm.applock.ui.icons.Display
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuProvider

enum class AppUsageMethod {
    ACCESSIBILITY,
    USAGE_STATS,
    SHIZUKU
}

private const val TAG = "AppIntroScreen"
private const val SHIZUKU_REQUEST_CODE = 423

private fun Context.isIgnoringBatteryOptimizations(): Boolean {
    val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
    return powerManager.isIgnoringBatteryOptimizations(packageName)
}

private fun Context.areNotificationsAllowed(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            NotificationManagerCompat.from(this).areNotificationsEnabled()

private fun Context.isShizukuPermissionGranted(): Boolean = runCatching {
    if (Shizuku.isPreV11()) {
        checkSelfPermission(this, ShizukuProvider.PERMISSION) == PermissionChecker.PERMISSION_GRANTED
    } else {
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }
}.getOrDefault(false)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PermissionStatus(granted: Boolean) {
    val container by animateColorAsState(
        if (granted) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        label = "status container"
    )
    Surface(shape = CircleShape, color = container) {
        AnimatedContent(
            targetState = granted,
            transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.8f)).togetherWith(fadeOut()) },
            label = "status"
        ) { isGranted ->
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (isGranted) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
                    contentDescription = null,
                    tint = if (isGranted) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = stringResource(
                        if (isGranted) R.string.intro_status_granted else R.string.intro_status_not_granted
                    ),
                    style = MaterialTheme.typography.labelLargeEmphasized,
                    color = if (isGranted) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MethodSelectionCard(
    title: String,
    description: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    index: Int = 0,
    count: Int = 1,
) {
    SegmentedListItem(
        selected = isSelected,
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        colors = ListItemDefaults.segmentedColors(),
        modifier = Modifier.semantics { role = Role.RadioButton },
        leadingContent = {
            ExpressiveShapeIcon(
                icon = icon,
                shape = if (isSelected) MaterialShapes.Cookie9Sided else MaterialShapes.Circle,
                containerColor = if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.secondaryContainer,
                contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSecondaryContainer,
                size = 40.dp
            )
        },
        trailingContent = {
            RadioButton(selected = isSelected, onClick = null)
        },
        supportingContent = { Text(description) }
    ) {
        Text(title, style = MaterialTheme.typography.titleMediumEmphasized)
    }
}

@SuppressLint("BatteryLife")
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppIntroScreen(navController: NavController) {
    val context = LocalContext.current

    var selectedMethod by remember { mutableStateOf(AppUsageMethod.ACCESSIBILITY) }
    var overlayPermissionGranted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var batteryOptimizationIgnored by remember { mutableStateOf(context.isIgnoringBatteryOptimizations()) }
    var notificationPermissionGranted by remember { mutableStateOf(context.areNotificationsAllowed()) }
    var notificationDeniedOnce by remember { mutableStateOf(false) }
    var usageStatsPermissionGranted by remember { mutableStateOf(context.hasUsagePermission()) }
    var accessibilityServiceEnabled by remember { mutableStateOf(context.isAccessibilityServiceEnabled()) }
    var shizukuPermissionGranted by remember { mutableStateOf(context.isShizukuPermissionGranted()) }

    fun refreshPermissions() {
        overlayPermissionGranted = Settings.canDrawOverlays(context)
        batteryOptimizationIgnored = context.isIgnoringBatteryOptimizations()
        notificationPermissionGranted = context.areNotificationsAllowed()
        usageStatsPermissionGranted = context.hasUsagePermission()
        accessibilityServiceEnabled = context.isAccessibilityServiceEnabled()
        shizukuPermissionGranted = context.isShizukuPermissionGranted()
    }

    LifecycleResumeEffect(Unit) {
        refreshPermissions()
        onPauseOrDispose { }
    }

    DisposableEffect(Unit) {
        val listener = Shizuku.OnRequestPermissionResultListener { requestCode, _ ->
            if (requestCode == SHIZUKU_REQUEST_CODE) {
                shizukuPermissionGranted = context.isShizukuPermissionGranted()
            }
        }
        runCatching { Shizuku.addRequestPermissionResultListener(listener) }
        onDispose { runCatching { Shizuku.removeRequestPermissionResultListener(listener) } }
    }

    fun openNotificationSettings() {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .onFailure { openAppInfoSettings(context) }
    }

    val requestPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            notificationPermissionGranted = isGranted || context.areNotificationsAllowed()
            if (!notificationPermissionGranted) {
                notificationDeniedOnce = true
                Toast.makeText(
                    context,
                    context.getString(R.string.notification_permission_required_desc),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    val shizukuPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            shizukuPermissionGranted = isGranted
            Toast.makeText(
                context,
                context.getString(
                    if (isGranted) R.string.shizuku_permission_granted
                    else R.string.shizuku_permission_required_desc
                ),
                Toast.LENGTH_SHORT
            ).show()
        }

    fun requestShizukuPermission() {
        try {
            if (Shizuku.isPreV11()) {
                shizukuPermissionLauncher.launch(ShizukuProvider.PERMISSION)
            } else if (!Shizuku.pingBinder()) {
                Toast.makeText(
                    context,
                    context.getString(R.string.intro_shizuku_not_running),
                    Toast.LENGTH_LONG
                ).show()
            } else {
                Shizuku.requestPermission(SHIZUKU_REQUEST_CODE)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Shizuku permission request failed", e)
            Toast.makeText(
                context,
                context.getString(R.string.intro_shizuku_not_running),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    val finishIntro = {
        AppIntroManager.markIntroAsCompleted(context)
        navController.navigate(Screen.SetPassword.route) {
            launchSingleTop = true
        }
    }

    val nextLabel = stringResource(R.string.next_button)
    val allowLabel = stringResource(R.string.intro_allow_button)
    fun labelFor(granted: Boolean) = if (granted) nextLabel else allowLabel

    val basicPages = listOf(
        IntroPage(
            title = stringResource(R.string.intro_welcome_title),
            description = stringResource(R.string.intro_welcome_desc),
            icon = Icons.Rounded.Lock,
            heroShape = MaterialShapes.Cookie9Sided,
        ),
        IntroPage(
            title = stringResource(R.string.secure_apps),
            description = stringResource(R.string.secure_apps_desc),
            icon = Icons.Rounded.Shield,
            heroShape = MaterialShapes.Clover4Leaf,
        ),
        IntroPage(
            title = stringResource(R.string.display_over_apps),
            description = stringResource(R.string.display_over_apps_desc),
            icon = Display,
            heroShape = MaterialShapes.Arch,
            nextButtonText = labelFor(overlayPermissionGranted),
            content = { PermissionStatus(overlayPermissionGranted) },
            onNext = {
                overlayPermissionGranted = Settings.canDrawOverlays(context)
                if (!overlayPermissionGranted) {
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    intent.data = "package:${context.packageName}".toUri()
                    Toast.makeText(
                        context,
                        context.getString(R.string.allow_display_over_other_apps),
                        Toast.LENGTH_LONG
                    ).show()
                    runCatching { context.startActivity(intent) }
                    false
                } else {
                    true
                }
            }
        ),
        IntroPage(
            title = stringResource(R.string.intro_battery_title),
            description = stringResource(R.string.intro_battery_desc),
            icon = BatterySaver,
            heroShape = MaterialShapes.Sunny,
            nextButtonText = labelFor(batteryOptimizationIgnored),
            content = {
                PermissionStatus(batteryOptimizationIgnored)
                Text(
                    text = stringResource(R.string.intro_battery_oem_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp)
                )
                OutlinedButton(
                    onClick = { openAppInfoSettings(context) },
                    shapes = ButtonDefaults.shapes()
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize)
                    )
                    Text(
                        stringResource(R.string.intro_open_app_info),
                        modifier = Modifier.padding(start = ButtonDefaults.IconSpacing)
                    )
                }
            },
            onNext = {
                batteryOptimizationIgnored = context.isIgnoringBatteryOptimizations()
                if (!batteryOptimizationIgnored) {
                    launchBatterySettings(context)
                    false
                } else {
                    true
                }
            }
        ),
        IntroPage(
            title = stringResource(R.string.notif_perm),
            description = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                stringResource(R.string.notif_perm_desc)
            else stringResource(R.string.notif_perm_granted),
            icon = Icons.Rounded.Notifications,
            heroShape = MaterialShapes.Cookie12Sided,
            nextButtonText = labelFor(notificationPermissionGranted),
            content = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                { PermissionStatus(notificationPermissionGranted) }
            } else null,
            onNext = {
                notificationPermissionGranted = context.areNotificationsAllowed()
                if (!notificationPermissionGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (notificationDeniedOnce) {
                        openNotificationSettings()
                    } else {
                        requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    false
                } else {
                    true
                }
            }
        )
    )

    val methodSelectionPage = IntroPage(
        title = stringResource(R.string.choose_app_detection),
        description = stringResource(R.string.app_detection_desc),
        icon = Icons.Rounded.Tune,
        heroShape = MaterialShapes.Puffy,
        content = {
            val methods = listOf(
                Triple(AppUsageMethod.ACCESSIBILITY, R.string.accessibility_service_title, R.string.accessibility_service_card_desc),
                Triple(AppUsageMethod.USAGE_STATS, R.string.usage_stats_title, R.string.usage_stats_card_desc),
                Triple(AppUsageMethod.SHIZUKU, R.string.shizuku_service_title, R.string.shizuku_service_card_desc),
            )
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
            ) {
                methods.forEachIndexed { index, (method, title, desc) ->
                    MethodSelectionCard(
                        title = stringResource(title),
                        description = stringResource(desc),
                        icon = when (method) {
                            AppUsageMethod.ACCESSIBILITY -> Accessibility
                            AppUsageMethod.USAGE_STATS -> Icons.Rounded.QueryStats
                            AppUsageMethod.SHIZUKU -> Icons.Rounded.AutoAwesome
                        },
                        isSelected = selectedMethod == method,
                        onClick = { selectedMethod = method },
                        index = index,
                        count = methods.size
                    )
                }
            }
            Text(
                text = stringResource(R.string.can_change_later_in_settings),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )
        },
        onNext = { true }
    )

    val methodSpecificPage = when (selectedMethod) {
        AppUsageMethod.ACCESSIBILITY -> IntroPage(
            title = stringResource(R.string.accessibility_service_title),
            description = stringResource(R.string.intro_accessibility_desc),
            icon = Accessibility,
            heroShape = MaterialShapes.Flower,
            nextButtonText = labelFor(accessibilityServiceEnabled),
            content = {
                PermissionStatus(accessibilityServiceEnabled)
                if (!accessibilityServiceEnabled) {
                    RestrictedSettingsHint(modifier = Modifier.padding(top = 8.dp))
                }
            },
            onNext = {
                accessibilityServiceEnabled = context.isAccessibilityServiceEnabled()
                if (!accessibilityServiceEnabled) {
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { context.startActivity(intent) }
                    false
                } else {
                    context.appLockRepository()
                        .setBackendImplementation(BackendImplementation.ACCESSIBILITY)
                    true
                }
            }
        )

        AppUsageMethod.USAGE_STATS -> IntroPage(
            title = stringResource(R.string.app_intro_usage_stats_title),
            description = stringResource(R.string.app_intro_usage_stats_desc),
            icon = Icons.Rounded.QueryStats,
            heroShape = MaterialShapes.SoftBurst,
            nextButtonText = labelFor(usageStatsPermissionGranted),
            content = { PermissionStatus(usageStatsPermissionGranted) },
            onNext = {
                usageStatsPermissionGranted = context.hasUsagePermission()
                if (!usageStatsPermissionGranted) {
                    val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { context.startActivity(intent) }
                    false
                } else {
                    context.appLockRepository()
                        .setBackendImplementation(BackendImplementation.USAGE_STATS)
                    runCatching {
                        context.startService(Intent(context, UsageLockService::class.java))
                    }.onFailure { Log.w(TAG, "Unable to start usage service", it) }
                    true
                }
            }
        )

        AppUsageMethod.SHIZUKU -> IntroPage(
            title = stringResource(R.string.shizuku_service_title),
            description = stringResource(R.string.app_intro_shizuku_desc),
            icon = Icons.Rounded.AutoAwesome,
            heroShape = MaterialShapes.Gem,
            nextButtonText = labelFor(shizukuPermissionGranted),
            content = { PermissionStatus(shizukuPermissionGranted) },
            onNext = {
                shizukuPermissionGranted = context.isShizukuPermissionGranted()
                if (!shizukuPermissionGranted) {
                    requestShizukuPermission()
                    false
                } else {
                    context.appLockRepository()
                        .setBackendImplementation(BackendImplementation.SHIZUKU)
                    runCatching {
                        context.startService(Intent(context, ShizukuAppLockService::class.java))
                    }.onFailure { Log.w(TAG, "Unable to start Shizuku service", it) }
                    true
                }
            }
        )
    }

    val finalPage = IntroPage(
        title = stringResource(R.string.app_intro_complete_privacy_title),
        description = stringResource(R.string.app_intro_complete_privacy_desc),
        icon = Icons.Rounded.VerifiedUser,
        heroShape = MaterialShapes.VerySunny,
        onNext = {
            refreshPermissions()

            val methodPermissionGranted = when (selectedMethod) {
                AppUsageMethod.ACCESSIBILITY -> accessibilityServiceEnabled
                AppUsageMethod.USAGE_STATS -> usageStatsPermissionGranted
                AppUsageMethod.SHIZUKU -> shizukuPermissionGranted
            }

            val allPermissionsGranted =
                overlayPermissionGranted && notificationPermissionGranted && methodPermissionGranted

            if (!allPermissionsGranted) {
                Toast.makeText(
                    context,
                    context.getString(R.string.all_permissions_required),
                    Toast.LENGTH_SHORT
                ).show()
            }
            allPermissionsGranted
        }
    )

    val allPages = basicPages + methodSelectionPage + methodSpecificPage + finalPage

    AppIntro(
        pages = allPages,
        onSkip = finishIntro,
        onFinish = finishIntro,
        showSkipButton = false,
        useAnimatedPager = true,
        nextButtonText = nextLabel,
        finishButtonText = stringResource(R.string.get_started_button),
        backButtonText = stringResource(R.string.intro_back),
        pageIndicatorDescription = { current, total ->
            context.getString(R.string.intro_page_indicator, current, total)
        }
    )
}
