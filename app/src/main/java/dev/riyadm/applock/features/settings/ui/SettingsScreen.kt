package dev.riyadm.applock.features.settings.ui

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockClock
import androidx.compose.material.icons.rounded.ManageHistory
import androidx.compose.material.icons.rounded.Password
import androidx.compose.material.icons.rounded.Pattern
import androidx.compose.material.icons.rounded.Pin
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.ShieldMoon
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Troubleshoot
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.NavController
import dev.riyadm.applock.AppLockApplication
import dev.riyadm.applock.R
import dev.riyadm.applock.core.broadcast.DeviceAdmin
import dev.riyadm.applock.core.navigation.Screen
import dev.riyadm.applock.core.utils.LogUtils
import dev.riyadm.applock.core.utils.isAccessibilityServiceEnabled
import dev.riyadm.applock.core.utils.openAccessibilitySettings
import dev.riyadm.applock.data.repository.AppLockRepository
import dev.riyadm.applock.data.repository.BackupRepository
import dev.riyadm.applock.data.repository.LockProfile
import dev.riyadm.applock.data.repository.PreferencesRepository
import dev.riyadm.applock.features.admin.AdminDisableActivity
import dev.riyadm.applock.features.profiles.ui.profileName
import dev.riyadm.applock.ui.icons.BrightnessHigh
import dev.riyadm.applock.ui.icons.Fingerprint
import dev.riyadm.applock.ui.icons.FingerprintOff
import dev.riyadm.applock.ui.icons.Timer
import dev.riyadm.applock.ui.theme.AppearanceSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val FAILED_ATTEMPT_OPTIONS = listOf(0, 3, 5, 10)
private val LOCKOUT_DURATION_OPTIONS = listOf(30, 60, 300)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val repository = remember {
        (context.applicationContext as? AppLockApplication)?.appLockRepository
            ?: AppLockRepository(context.applicationContext)
    }

    var reloadKey by remember { mutableIntStateOf(0) }
    val state = remember(reloadKey) { SettingsState(repository) }
    val snackbarHostState = remember { SnackbarHostState() }

    var biometricAvailable by remember { mutableStateOf(repository.isBiometricAuthEnabled()) }
    var resumeCount by remember { mutableIntStateOf(0) }
    var showUnlockDurationSheet by remember { mutableStateOf(false) }
    val profilesState by repository.profiles.state.collectAsStateWithLifecycle()
    // Set only when the active profile overrides the global unlock duration.
    val overridingProfile = profilesState.active.takeIf { it.unlockDurationMinutes != null }
    var showPermissionDialog by remember { mutableStateOf(false) }
    var showDeviceAdminDialog by remember { mutableStateOf(false) }
    var showAccessibilityDialog by remember { mutableStateOf(false) }
    var restoreResult by remember { mutableStateOf<BackupRepository.ImportResult?>(null) }
    // The user asked for anti-uninstall but still had to grant permissions in other screens
    var pendingAntiUninstall by rememberSaveable { mutableStateOf(false) }

    LifecycleResumeEffect(state) {
        state.refreshExternal()
        resumeCount++
        if (pendingAntiUninstall && !state.antiUninstall) {
            val hasAdmin = context.hasDeviceAdmin()
            val hasAccessibility = context.isAccessibilityServiceEnabled()
            when {
                hasAdmin && hasAccessibility -> {
                    state.antiUninstall = true
                    pendingAntiUninstall = false
                }

                hasAdmin -> showAccessibilityDialog = true
            }
        }
        onPauseOrDispose { }
    }

    LaunchedEffect(resumeCount) {
        biometricAvailable = withContext(Dispatchers.Default) { isBiometricAvailable(context) }
    }

    fun requestDeviceAdmin() {
        val component = ComponentName(context, DeviceAdmin::class.java)
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component)
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                context.getString(R.string.main_screen_device_admin_explanation)
            )
        }
        context.startActivity(intent)
    }

    fun onAntiUninstallChanged(enable: Boolean) {
        if (!enable) {
            pendingAntiUninstall = false
            context.startActivity(Intent(context, AdminDisableActivity::class.java))
            return
        }
        val hasAdmin = context.hasDeviceAdmin()
        val hasAccessibility = context.isAccessibilityServiceEnabled()
        if (hasAdmin && hasAccessibility) {
            state.antiUninstall = true
            return
        }
        pendingAntiUninstall = true
        when {
            !hasAdmin && !hasAccessibility -> showPermissionDialog = true
            !hasAdmin -> showDeviceAdminDialog = true
            else -> showAccessibilityDialog = true
        }
    }

    if (showUnlockDurationSheet) {
        UnlockDurationSheet(
            currentDuration = state.unlockDuration,
            description = overridingProfile?.let { profile ->
                stringResource(
                    R.string.followups_unlock_duration_sheet_desc_profile,
                    profileName(profile),
                    unlockDurationLabel(profile.unlockDurationMinutes ?: 0)
                )
            },
            onDismiss = { showUnlockDurationSheet = false },
            onSelect = { state.unlockDuration = it }
        )
    }

    if (showPermissionDialog) {
        PermissionRequiredDialog(
            onDismiss = {
                showPermissionDialog = false
                pendingAntiUninstall = false
            },
            onConfirm = {
                showPermissionDialog = false
                showDeviceAdminDialog = true
            }
        )
    }

    if (showDeviceAdminDialog) {
        DeviceAdminDialog(
            onDismiss = {
                showDeviceAdminDialog = false
                pendingAntiUninstall = false
            },
            onConfirm = {
                showDeviceAdminDialog = false
                requestDeviceAdmin()
            }
        )
    }

    if (showAccessibilityDialog) {
        AccessibilityDialog(
            onDismiss = {
                showAccessibilityDialog = false
                pendingAntiUninstall = false
            },
            onConfirm = {
                showAccessibilityDialog = false
                openAccessibilitySettings(context)
                if (!context.hasDeviceAdmin()) showDeviceAdminDialog = true
            }
        )
    }

    restoreResult?.let { result ->
        RestoreCompleteDialog(
            lockedApps = result.lockedApps,
            settings = result.settings,
            onDismiss = { restoreResult = null }
        )
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val layoutDirection = LocalLayoutDirection.current
    val onBack = dropUnlessResumed { navController.popBackStack() }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.settings_screen_title)) },
                subtitle = { Text(stringResource(R.string.settings_about_app_name)) },
                navigationIcon = {
                    IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.settings_screen_back_cd)
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.surface
    ) { innerPadding ->
        CompositionLocalProvider(LocalSettingsHapticsEnabled provides !state.disableHaptics) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = innerPadding.calculateStartPadding(layoutDirection) + 16.dp,
                    end = innerPadding.calculateEndPadding(layoutDirection) + 16.dp,
                    top = innerPadding.calculateTopPadding(),
                    bottom = innerPadding.calculateBottomPadding() + 32.dp
                )
            ) {
                item(key = "appearance", contentType = "section") { AppearanceSection(repository) }
                item(key = "lock_screen", contentType = "section") { LockScreenSection(state, biometricAvailable) }
                item(key = "security", contentType = "section") {
                    SecuritySection(
                        state = state,
                        overridingProfile = overridingProfile,
                        onChangeCredential = { navController.navigate(Screen.ChangePassword.route) },
                        onUnlockDurationClick = { showUnlockDurationSheet = true }
                    )
                }
                item(key = "protection", contentType = "section") {
                    ProtectionSection(state = state, onAntiUninstallChanged = ::onAntiUninstallChanged)
                }
                item(key = "trusted", contentType = "section") {
                    TrustedNetworksSection(onOpenTrustedNetworks = { navController.navigate(Screen.TrustedNetworks.route) })
                }
                item(key = "privacy", contentType = "section") {
                    PrivacySection(
                        state = state,
                        onViewHistory = { navController.navigate(Screen.UnlockHistory.route) }
                    )
                }
                item(key = "intruder", contentType = "section") {
                    IntruderSection(onViewPhotos = { navController.navigate(Screen.IntruderPhotos.route) })
                }
                item(key = "hardening", contentType = "section") { HardeningSection(state.unlockLog) }
                item(key = "backup", contentType = "section") {
                    BackupSection(
                        repository = repository,
                        snackbarHostState = snackbarHostState,
                        onImported = { result ->
                            AppearanceSettings.load(repository)
                            applyBackend(context, repository, repository.getBackendImplementation())
                            reloadKey++
                            restoreResult = result
                        }
                    )
                }
                item(key = "updates", contentType = "section") { UpdatesSection(state) }
                item(key = "backend", contentType = "section") {
                    BackendSection(
                        repository = repository,
                        selected = state.backend,
                        onBackendChanged = state::onBackendChanged
                    )
                }
                item(key = "advanced", contentType = "section") { AdvancedSection(state) }
                item(key = "about", contentType = "about") { AboutSection(repository, snackbarHostState) }
            }
        }
    }
}

@Composable
private fun LockScreenSection(state: SettingsState, biometricAvailable: Boolean) {
    val isPin = state.lockType == PreferencesRepository.LOCK_TYPE_PIN
    SettingsGroup(title = stringResource(R.string.settings_section_lock_screen)) {
        item { shapes ->
            SwitchSettingItem(
                shapes = shapes,
                icon = BrightnessHigh,
                title = stringResource(R.string.settings_screen_max_brightness_title),
                summary = stringResource(R.string.settings_screen_max_brightness_desc),
                checked = state.maxBrightness,
                onCheckedChange = { state.maxBrightness = it }
            )
        }
        item { shapes ->
            SwitchSettingItem(
                shapes = shapes,
                icon = if (state.biometricAuth && biometricAvailable) Fingerprint else FingerprintOff,
                title = stringResource(R.string.settings_screen_biometric_auth_title),
                summary = stringResource(
                    if (biometricAvailable) R.string.settings_biometric_desc_available
                    else R.string.settings_biometric_desc_unavailable
                ),
                checked = state.biometricAuth && biometricAvailable,
                enabled = biometricAvailable,
                onCheckedChange = { state.biometricAuth = it }
            )
        }
        item { shapes ->
            val enabled = biometricAvailable && state.biometricAuth
            SwitchSettingItem(
                shapes = shapes,
                icon = Icons.Rounded.Security,
                title = stringResource(R.string.settings_biometric_auto_prompt_title),
                summary = stringResource(
                    if (enabled) R.string.settings_biometric_auto_prompt_desc
                    else R.string.settings_biometric_auto_prompt_desc_disabled
                ),
                checked = state.biometricAutoPrompt && enabled,
                enabled = enabled,
                onCheckedChange = { state.biometricAutoPrompt = it }
            )
        }
        item { shapes ->
            SwitchSettingItem(
                shapes = shapes,
                icon = Icons.Rounded.Dialpad,
                title = stringResource(R.string.settings_scramble_keypad_title),
                summary = stringResource(
                    if (isPin) R.string.settings_scramble_keypad_desc
                    else R.string.settings_scramble_keypad_desc_pin_only
                ),
                checked = state.scrambleKeypad && isPin,
                enabled = isPin,
                onCheckedChange = { state.scrambleKeypad = it }
            )
        }
        item { shapes ->
            SwitchSettingItem(
                shapes = shapes,
                icon = Icons.Rounded.ShieldMoon,
                title = stringResource(R.string.settings_screen_auto_unlock_title),
                summary = stringResource(R.string.settings_auto_unlock_desc),
                checked = state.autoUnlock,
                onCheckedChange = { state.autoUnlock = it }
            )
        }
        item { shapes ->
            SwitchSettingItem(
                shapes = shapes,
                icon = Icons.Rounded.Vibration,
                title = stringResource(R.string.settings_disable_haptics_title),
                summary = stringResource(R.string.settings_screen_haptic_feedback_desc),
                checked = state.disableHaptics,
                onCheckedChange = { state.disableHaptics = it }
            )
        }
    }
}

@Composable
private fun SecuritySection(
    state: SettingsState,
    overridingProfile: LockProfile?,
    onChangeCredential: () -> Unit,
    onUnlockDurationClick: () -> Unit
) {
    val attemptOptions = FAILED_ATTEMPT_OPTIONS.map {
        ChoiceOption(
            it,
            if (it == 0) stringResource(R.string.settings_failed_attempts_off) else it.toString()
        )
    }
    val lockoutOptions = LOCKOUT_DURATION_OPTIONS.map {
        ChoiceOption(
            it,
            if (it < 60) stringResource(R.string.settings_lockout_seconds_short, it)
            else stringResource(R.string.settings_lockout_minutes_short, it / 60)
        )
    }

    SettingsGroup(title = stringResource(R.string.settings_screen_security_title)) {
        item { shapes ->
            val (icon, title, summary) = when (state.lockType) {
                PreferencesRepository.LOCK_TYPE_PATTERN -> Triple(
                    Icons.Rounded.Pattern,
                    R.string.settings_change_pattern_title,
                    R.string.settings_change_pattern_desc
                )

                PreferencesRepository.LOCK_TYPE_PASSWORD -> Triple(
                    Icons.Rounded.Password,
                    R.string.settings_change_password_title,
                    R.string.settings_change_password_desc
                )

                else -> Triple(
                    Icons.Rounded.Pin,
                    R.string.settings_screen_change_pin_title,
                    R.string.settings_screen_change_pin_desc
                )
            }
            ActionSettingItem(
                shapes = shapes,
                icon = icon,
                title = stringResource(title),
                summary = stringResource(summary),
                onClick = onChangeCredential
            )
        }
        item { shapes ->
            ActionSettingItem(
                shapes = shapes,
                icon = Timer,
                title = stringResource(R.string.settings_screen_unlock_duration_title),
                summary = if (overridingProfile != null) {
                    stringResource(
                        R.string.followups_unlock_duration_by_profile,
                        unlockDurationLabel(overridingProfile.unlockDurationMinutes ?: 0),
                        profileName(overridingProfile)
                    )
                } else unlockDurationLabel(state.unlockDuration),
                onClick = onUnlockDurationClick
            )
        }
        item { shapes ->
            ChoiceSettingItem(
                shapes = shapes,
                icon = Icons.Rounded.LockClock,
                title = stringResource(R.string.settings_failed_attempts_title),
                summary = stringResource(R.string.settings_failed_attempts_desc),
                options = attemptOptions,
                selected = state.maxFailedAttempts,
                onSelect = { state.maxFailedAttempts = it }
            )
        }
        item { shapes ->
            val enabled = state.maxFailedAttempts > 0
            ChoiceSettingItem(
                shapes = shapes,
                icon = Icons.Rounded.HourglassTop,
                title = stringResource(R.string.settings_lockout_duration_title),
                summary = stringResource(
                    if (enabled) R.string.settings_lockout_duration_desc
                    else R.string.settings_lockout_duration_desc_disabled
                ),
                options = lockoutOptions,
                selected = state.lockoutSeconds,
                onSelect = { state.lockoutSeconds = it },
                enabled = enabled
            )
        }
    }
}

@Composable
private fun ProtectionSection(state: SettingsState, onAntiUninstallChanged: (Boolean) -> Unit) {
    SettingsGroup(title = stringResource(R.string.settings_section_protection)) {
        item { shapes ->
            SwitchSettingItem(
                shapes = shapes,
                icon = Icons.Rounded.Lock,
                title = stringResource(R.string.settings_screen_anti_uninstall_title),
                summary = stringResource(R.string.settings_screen_anti_uninstall_desc),
                checked = state.antiUninstall,
                onCheckedChange = onAntiUninstallChanged,
                accent = SettingsAccent.Secondary
            )
        }
        item { shapes ->
            SwitchSettingItem(
                shapes = shapes,
                icon = Icons.Rounded.InstallMobile,
                title = stringResource(R.string.settings_auto_lock_new_apps_title),
                summary = stringResource(R.string.settings_auto_lock_new_apps_desc),
                checked = state.autoLockNewApps,
                onCheckedChange = { state.autoLockNewApps = it },
                accent = SettingsAccent.Secondary
            )
        }
    }
}

@Composable
private fun PrivacySection(state: SettingsState, onViewHistory: () -> Unit) {
    SettingsGroup(title = stringResource(R.string.settings_section_privacy)) {
        item { shapes ->
            SwitchSettingItem(
                shapes = shapes,
                icon = Icons.Rounded.ManageHistory,
                title = stringResource(R.string.settings_unlock_history_title),
                summary = stringResource(R.string.settings_unlock_history_desc),
                checked = state.unlockLog,
                onCheckedChange = { state.unlockLog = it },
                accent = SettingsAccent.Tertiary
            )
        }
        item { shapes ->
            ActionSettingItem(
                shapes = shapes,
                icon = Icons.Rounded.History,
                title = stringResource(R.string.settings_view_unlock_history_title),
                summary = stringResource(R.string.settings_view_unlock_history_desc),
                onClick = onViewHistory,
                accent = SettingsAccent.Tertiary
            )
        }
    }
}

@Composable
private fun UpdatesSection(state: SettingsState) {
    SettingsGroup(
        title = stringResource(R.string.settings_section_updates),
        footer = stringResource(R.string.settings_update_check_footer)
    ) {
        item { shapes ->
            SwitchSettingItem(
                shapes = shapes,
                icon = Icons.Rounded.SystemUpdate,
                title = stringResource(R.string.settings_update_check_title),
                summary = stringResource(R.string.settings_update_check_desc),
                checked = state.updateCheck,
                onCheckedChange = { state.updateCheck = it },
                accent = SettingsAccent.Secondary
            )
        }
    }
}

@Composable
private fun AdvancedSection(state: SettingsState) {
    val context = LocalContext.current
    SettingsGroup(title = stringResource(R.string.settings_screen_advanced_title)) {
        item { shapes ->
            ActionSettingItem(
                shapes = shapes,
                icon = Icons.Rounded.Security,
                title = stringResource(R.string.settings_Screen_export_audit),
                summary = stringResource(R.string.settings_screen_export_audit_desc),
                accent = SettingsAccent.Secondary,
                onClick = {
                    shareLogFile(
                        context,
                        LogUtils.exportAuditLogs(),
                        context.getString(R.string.settings_share_audit_logs)
                    )
                }
            )
        }
        item { shapes ->
            ActionSettingItem(
                shapes = shapes,
                icon = Icons.Rounded.BugReport,
                title = stringResource(R.string.settings_screen_export_logs_title),
                summary = stringResource(R.string.settings_screen_export_logs_desc),
                accent = SettingsAccent.Secondary,
                onClick = {
                    shareLogFile(
                        context,
                        LogUtils.exportLogs(),
                        context.getString(R.string.settings_share_logs)
                    )
                }
            )
        }
        item { shapes ->
            SwitchSettingItem(
                shapes = shapes,
                icon = Icons.Rounded.Troubleshoot,
                title = stringResource(R.string.settings_logging_title),
                summary = stringResource(R.string.settings_logging_desc),
                checked = state.logging,
                onCheckedChange = {
                    state.logging = it
                    LogUtils.setLoggingEnabled(it)
                },
                accent = SettingsAccent.Secondary
            )
        }
    }
}

private fun shareLogFile(context: Context, uri: android.net.Uri?, chooserTitle: String) {
    if (uri == null) {
        Toast.makeText(
            context,
            context.getString(R.string.settings_screen_export_logs_error),
            Toast.LENGTH_SHORT
        ).show()
        return
    }
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(shareIntent, chooserTitle))
}

private fun Context.hasDeviceAdmin(): Boolean {
    val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    return dpm.isAdminActive(ComponentName(this, DeviceAdmin::class.java))
}

private fun isBiometricAvailable(context: Context): Boolean =
    BiometricManager.from(context).canAuthenticate(
        BiometricManager.Authenticators.BIOMETRIC_WEAK or
                BiometricManager.Authenticators.BIOMETRIC_STRONG
    ) == BiometricManager.BIOMETRIC_SUCCESS
