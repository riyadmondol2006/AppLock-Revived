package dev.riyadm.applock.features.settings.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import dev.riyadm.applock.R
import dev.riyadm.applock.core.utils.appLockRepository
import dev.riyadm.applock.core.utils.openAppInfoSettings
import dev.riyadm.applock.features.notifications.LockedAppNotificationListener
import dev.riyadm.applock.ui.components.ExpressivePermissionDialog

/**
 * Opt-in "hide notifications of locked apps". The notification access permission is only ever
 * requested after the user turns the switch on, and the feature is never enabled by itself.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HideNotificationsSection() {
    val context = LocalContext.current
    val repository = remember(context) { context.appLockRepository() }
    val haptics = rememberSettingsHaptics()

    var enabled by remember { mutableStateOf(repository.isHideLockedNotificationsEnabled()) }
    var mute by remember { mutableStateOf(repository.isHideLockedNotificationsMute()) }
    var hasAccess by remember { mutableStateOf(LockedAppNotificationListener.hasAccess(context)) }
    var ownNotificationsOn by remember {
        mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled())
    }
    var showExplanation by rememberSaveable { mutableStateOf(false) }
    // The user asked to turn the feature on and went to grant access in the system settings.
    var waitingForAccess by rememberSaveable { mutableStateOf(false) }

    fun turnOn() {
        repository.setHideLockedNotificationsEnabled(true)
        enabled = true
        LockedAppNotificationListener.requestRebindIfPossible(context)
        haptics.confirm()
    }

    fun turnOff() {
        repository.setHideLockedNotificationsEnabled(false)
        enabled = false
        waitingForAccess = false
        LockedAppNotificationListener.onDisabled(context)
    }

    // Access is granted in another screen; pick it up when the user comes back.
    LifecycleResumeEffect(Unit) {
        hasAccess = LockedAppNotificationListener.hasAccess(context)
        ownNotificationsOn = NotificationManagerCompat.from(context).areNotificationsEnabled()
        enabled = repository.isHideLockedNotificationsEnabled()
        if (waitingForAccess) {
            waitingForAccess = false
            if (hasAccess) turnOn() else haptics.reject()
        }
        onPauseOrDispose { }
    }

    if (showExplanation) {
        val appName = stringResource(R.string.app_name)
        ExpressivePermissionDialog(
            icon = Icons.Rounded.NotificationsOff,
            iconShape = MaterialShapes.Cookie9Sided,
            title = stringResource(R.string.hide_notifications_dialog_title),
            text = stringResource(R.string.hide_notifications_dialog_text, appName),
            supportingText = stringResource(R.string.hide_notifications_dialog_privacy) + "\n\n" +
                    stringResource(R.string.hide_notifications_dialog_restricted, appName),
            confirmText = stringResource(R.string.hide_notifications_dialog_continue),
            onConfirm = {
                showExplanation = false
                waitingForAccess = true
                if (!openNotificationAccessSettings(context)) {
                    waitingForAccess = false
                    openAppInfoSettings(context)
                }
            },
            onDismiss = { showExplanation = false },
            dismissText = stringResource(R.string.hide_notifications_dialog_not_now)
        )
    }

    val active = enabled && hasAccess
    val needsAccess = enabled && !hasAccess
    val modeOptions = listOf(
        ChoiceOption(false, stringResource(R.string.hide_notifications_mode_content)),
        ChoiceOption(true, stringResource(R.string.hide_notifications_mode_mute)),
    )

    SettingsGroup(
        title = stringResource(R.string.hide_notifications_section_title),
        footer = stringResource(R.string.hide_notifications_footer)
    ) {
        item { shapes ->
            SwitchSettingItem(
                shapes = shapes,
                icon = Icons.Rounded.VisibilityOff,
                title = stringResource(R.string.hide_notifications_switch_title),
                summary = stringResource(
                    when {
                        needsAccess -> R.string.hide_notifications_switch_desc_no_access
                        active && !mute && !ownNotificationsOn ->
                            R.string.hide_notifications_switch_desc_no_notifications

                        else -> R.string.hide_notifications_switch_desc
                    }
                ),
                checked = active,
                onCheckedChange = { on ->
                    when {
                        !on -> turnOff()
                        hasAccess -> turnOn()
                        else -> showExplanation = true
                    }
                },
                accent = if (needsAccess) SettingsAccent.Error else SettingsAccent.Tertiary
            )
        }
        item { shapes ->
            ChoiceSettingItem(
                shapes = shapes,
                icon = Icons.Rounded.NotificationsOff,
                title = stringResource(R.string.hide_notifications_mode_title),
                summary = stringResource(R.string.hide_notifications_mode_desc),
                options = modeOptions,
                selected = mute,
                onSelect = {
                    repository.setHideLockedNotificationsMute(it)
                    mute = it
                    if (it) LockedAppNotificationListener.onDisabled(context)
                },
                enabled = active,
                accent = SettingsAccent.Tertiary
            )
        }
    }
}

/** Opens the system's list of apps with notification access; false if the device has no such screen. */
private fun openNotificationAccessSettings(context: Context): Boolean = try {
    context.startActivity(
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
    true
} catch (_: ActivityNotFoundException) {
    false
}
