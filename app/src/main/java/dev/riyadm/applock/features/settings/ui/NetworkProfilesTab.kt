package dev.riyadm.applock.features.settings.ui

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.SignalWifiBad
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.riyadm.applock.R
import dev.riyadm.applock.core.trusted.NetworkProfileRule
import dev.riyadm.applock.core.trusted.NetworkProfileSettings
import dev.riyadm.applock.core.trusted.TrustedNetwork
import dev.riyadm.applock.core.trusted.TrustedNetworkMonitor
import dev.riyadm.applock.core.trusted.WifiTrust
import dev.riyadm.applock.data.repository.LockProfile
import dev.riyadm.applock.features.profiles.ui.icon
import dev.riyadm.applock.features.profiles.ui.profileName
import dev.riyadm.applock.ui.components.ExpressivePermissionDialog
import dev.riyadm.applock.ui.components.ExpressiveShapeIcon

/** Settings and rules of the "Network profiles" tab, kept in memory next to the stored values. */
@Stable
class NetworkProfilesUiState(private val settings: NetworkProfileSettings) {
    var enabled by mutableStateOf(settings.isEnabled())
        private set
    var defaultProfileId by mutableStateOf(settings.getDefaultProfileId())
        private set
    var rules by mutableStateOf(settings.getRules())
        private set

    fun reload() {
        enabled = settings.isEnabled()
        defaultProfileId = settings.getDefaultProfileId()
        rules = settings.getRules()
    }

    fun turn(on: Boolean) {
        settings.setEnabled(on)
        enabled = on
    }

    fun setDefault(id: String?) {
        settings.setDefaultProfileId(id)
        defaultProfileId = id
    }

    fun setRule(rule: NetworkProfileRule) {
        settings.setRule(rule)
        rules = settings.getRules()
    }

    fun removeRule(network: TrustedNetwork) {
        settings.removeRule(network)
        rules = settings.getRules()
    }
}

@Composable
fun rememberNetworkProfilesState(settings: NetworkProfileSettings): NetworkProfilesUiState =
    remember(settings) { NetworkProfilesUiState(settings) }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun NetworkProfilesEnableDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    ExpressivePermissionDialog(
        icon = Icons.Rounded.SwapHoriz,
        title = stringResource(R.string.network_profiles_enable_title),
        text = stringResource(R.string.network_profiles_enable_text),
        supportingText = stringResource(R.string.network_profiles_enable_supporting),
        confirmText = stringResource(R.string.network_profiles_enable_confirm),
        iconShape = MaterialShapes.Cookie7Sided,
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
}

/**
 * The tab's list rows. [profiles] are the existing lock profiles; [onRequestEnable] opens the
 * confirmation dialog (turning it off needs none, because it only stops automatic switching).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal fun LazyListScope.networkProfilesItems(
    state: NetworkProfilesUiState,
    profiles: List<LockProfile>,
    status: TrustedNetworkMonitor.Status,
    onRequestEnable: () -> Unit,
    onLinked: (ssid: String, profileName: String) -> Unit,
) {
    item(key = "np_master", contentType = "master") {
        SettingsGroup(title = null, modifier = Modifier.animateItem()) {
            item { shapes ->
                SwitchSettingItem(
                    shapes = shapes,
                    icon = Icons.Rounded.SwapHoriz,
                    title = stringResource(R.string.network_profiles_master_switch),
                    summary = stringResource(
                        if (state.enabled) R.string.network_profiles_desc_on else R.string.network_profiles_desc_off
                    ),
                    checked = state.enabled,
                    onCheckedChange = { checked -> if (checked) onRequestEnable() else state.turn(false) },
                    accent = SettingsAccent.Tertiary
                )
            }
        }
    }

    item(key = "np_default_header", contentType = "header") {
        SettingsSectionHeader(
            stringResource(R.string.network_profiles_default_header),
            modifier = Modifier.animateItem()
        )
    }
    item(key = "np_default", contentType = "default") {
        val noneLabel = stringResource(R.string.network_profiles_default_none)
        val selected = profiles.firstOrNull { it.id == state.defaultProfileId }
        var expanded by remember { mutableStateOf(false) }
        SettingsGroup(title = null, modifier = Modifier.animateItem()) {
            item { shapes ->
                ActionSettingItem(
                    shapes = shapes,
                    icon = Icons.Rounded.Wifi,
                    title = stringResource(R.string.network_profiles_default_title),
                    summary = stringResource(R.string.network_profiles_default_summary),
                    onClick = { expanded = true },
                    accent = SettingsAccent.Secondary,
                    trailing = {
                        ProfileMenu(
                            expanded = expanded,
                            onExpandedChange = { expanded = it },
                            label = selected?.let { profileName(it) } ?: noneLabel,
                            profiles = profiles,
                            noneLabel = noneLabel,
                            onSelect = { state.setDefault(it) }
                        )
                    }
                )
            }
        }
    }

    item(key = "np_current_header", contentType = "header") {
        SettingsSectionHeader(
            stringResource(R.string.trusted_current_header),
            modifier = Modifier.animateItem()
        )
    }
    item(key = "np_current", contentType = "current") {
        CurrentNetworkProfileCard(
            status = status,
            state = state,
            profiles = profiles,
            onLinked = onLinked,
            modifier = Modifier.animateItem()
        )
    }

    item(key = "np_linked_header", contentType = "header") {
        SettingsSectionHeader(
            stringResource(R.string.network_profiles_linked_header),
            modifier = Modifier.animateItem()
        )
    }
    if (state.rules.isEmpty()) {
        item(key = "np_linked_empty", contentType = "empty") {
            Text(
                text = stringResource(R.string.network_profiles_linked_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .animateItem()
                    .padding(horizontal = 16.dp)
            )
        }
    } else {
        itemsIndexed(
            items = state.rules,
            key = { _, rule -> "np_rule_${rule.network.ssid}_${rule.network.bssid}" },
            contentType = { _, _ -> "rule" }
        ) { index, rule ->
            LinkedNetworkItem(
                rule = rule,
                shapes = ListItemDefaults.segmentedShapes(index, state.rules.size),
                profiles = profiles,
                connected = status.connections.any {
                    WifiTrust.matches(rule.network, it, Build.VERSION.SDK_INT)
                },
                onSelect = { id -> state.setRule(rule.copy(profileId = id)) },
                onRemove = { state.removeRule(rule.network) },
                modifier = Modifier
                    .animateItem()
                    .padding(top = if (index == 0) 0.dp else ListItemDefaults.SegmentedGap)
            )
        }
    }

    item(key = "np_footer", contentType = "footer") {
        Text(
            text = stringResource(R.string.network_profiles_footer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .animateItem()
                .padding(start = 16.dp, end = 16.dp, top = 16.dp)
        )
    }
}

/** A text button that opens a menu of profiles; [noneLabel] adds a "no profile" entry. */
@Composable
private fun ProfileMenu(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    label: String,
    profiles: List<LockProfile>,
    noneLabel: String?,
    onSelect: (String?) -> Unit,
) {
    Box {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 8.dp)
            )
            Icon(
                Icons.Rounded.ArrowDropDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { onExpandedChange(false) }) {
            if (noneLabel != null) {
                DropdownMenuItem(
                    text = { Text(noneLabel) },
                    onClick = {
                        onExpandedChange(false)
                        onSelect(null)
                    }
                )
            }
            profiles.forEach { profile ->
                DropdownMenuItem(
                    text = { Text(profileName(profile), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = { Icon(profile.kind.icon, contentDescription = null) },
                    onClick = {
                        onExpandedChange(false)
                        onSelect(profile.id)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LinkedNetworkItem(
    rule: NetworkProfileRule,
    shapes: ListItemShapes,
    profiles: List<LockProfile>,
    connected: Boolean,
    onSelect: (String) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val network = rule.network
    val name = network.displayName
    val profile = profiles.firstOrNull { it.id == rule.profileId }
    val detail = network.bssid?.let { stringResource(R.string.trusted_saved_exact_router, it) }
        ?: if (WifiTrust.routerRequired(Build.VERSION.SDK_INT)) {
            stringResource(R.string.followups_trusted_saved_needs_router)
        } else {
            stringResource(R.string.trusted_saved_any_router)
        }
    val supporting = buildString {
        if (connected) append(stringResource(R.string.trusted_saved_connected)).append(" · ")
        append(if (profile == null) stringResource(R.string.network_profiles_deleted_profile_note) else detail)
    }
    var expanded by remember { mutableStateOf(false) }
    val haptics = rememberSettingsHaptics()

    SegmentedListItem(
        onClick = { expanded = true },
        shapes = shapes,
        modifier = modifier,
        leadingContent = {
            SettingsIcon(
                if (network.bssid != null) Icons.Rounded.Router else Icons.Rounded.Wifi,
                if (connected) SettingsAccent.Tertiary else SettingsAccent.Secondary
            )
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProfileMenu(
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                    label = profile?.let { profileName(it) }
                        ?: stringResource(R.string.network_profiles_deleted_profile),
                    profiles = profiles,
                    noneLabel = null,
                    onSelect = { id -> id?.let(onSelect) }
                )
                IconButton(
                    onClick = {
                        haptics.press()
                        onRemove()
                    },
                    shapes = IconButtonDefaults.shapes()
                ) {
                    Icon(
                        Icons.Rounded.DeleteOutline,
                        contentDescription = stringResource(R.string.trusted_remove_cd, name)
                    )
                }
            }
        },
        supportingContent = { Text(supporting) },
        colors = settingsItemColors(),
        content = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CurrentNetworkProfileCard(
    status: TrustedNetworkMonitor.Status,
    state: NetworkProfilesUiState,
    profiles: List<LockProfile>,
    onLinked: (ssid: String, profileName: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val wifi = status.wifi
    val sdk = Build.VERSION.SDK_INT
    val existing = remember(wifi, state.rules) {
        wifi?.let { current -> state.rules.firstOrNull { WifiTrust.matches(it.network, current, sdk) } }
    }
    val existingProfile = profiles.firstOrNull { it.id == existing?.profileId }
    val routerRequired = WifiTrust.routerRequired(sdk)
    var matchRouter by remember(wifi?.ssid) { mutableStateOf(true) }
    var chosenId by remember(wifi?.ssid) { mutableStateOf<String?>(null) }
    var showNameOnlyWarning by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val chosen = profiles.firstOrNull { it.id == chosenId } ?: profiles.firstOrNull()
    val chosenName = chosen?.let { profileName(it) }

    if (showNameOnlyWarning) {
        ExpressivePermissionDialog(
            icon = Icons.Rounded.SignalWifiBad,
            title = stringResource(R.string.followups_trusted_name_only_title),
            text = stringResource(R.string.followups_trusted_name_only_text),
            confirmText = stringResource(R.string.followups_trusted_name_only_confirm),
            iconShape = MaterialShapes.SoftBurst,
            onConfirm = {
                showNameOnlyWarning = false
                matchRouter = false
            },
            onDismiss = { showNameOnlyWarning = false }
        )
    }

    val (icon: ImageVector, title) = when {
        wifi != null -> Icons.Rounded.Wifi to wifi.displayName
        status.onWifi -> Icons.Rounded.Wifi to stringResource(R.string.trusted_current_unknown)
        else -> Icons.Rounded.WifiOff to stringResource(R.string.trusted_current_not_connected)
    }
    val description = when {
        wifi == null && status.onWifi -> stringResource(R.string.trusted_current_unknown_desc)
        wifi == null -> null
        !wifi.canBeTrusted && !wifi.securityKnown -> stringResource(R.string.followups_trusted_security_unknown)
        !wifi.canBeTrusted -> stringResource(R.string.network_profiles_current_open)
        existingProfile != null ->
            stringResource(R.string.network_profiles_current_mapped, profileName(existingProfile))

        else -> stringResource(R.string.network_profiles_current_hint)
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = if (existingProfile != null) colors.tertiaryContainer else colors.surfaceContainer,
        contentColor = if (existingProfile != null) colors.onTertiaryContainer else colors.onSurface
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ExpressiveShapeIcon(
                    icon = icon,
                    shape = if (existingProfile != null) MaterialShapes.Cookie9Sided else MaterialShapes.Circle,
                    containerColor = if (existingProfile != null) colors.tertiary else colors.secondaryContainer,
                    contentColor = if (existingProfile != null) colors.onTertiary else colors.onSecondaryContainer,
                    size = 56.dp
                )
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLargeEmphasized,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (description != null) {
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (wifi?.canBeTrusted == false) colors.error
                            else LocalContentColor.current.copy(alpha = 0.85f)
                        )
                    }
                }
            }

            if (wifi != null && existing == null && wifi.canBeTrusted && chosen != null) {
                Spacer(Modifier.height(16.dp))
                val pinRouter = matchRouter || routerRequired
                val canSave = WifiTrust.canSave(wifi, pinRouter, sdk)
                SwitchSettingItem(
                    shapes = ListItemDefaults.segmentedShapes(0, 1),
                    icon = Icons.Rounded.Router,
                    title = stringResource(R.string.trusted_match_router_title),
                    summary = stringResource(
                        when {
                            routerRequired -> R.string.followups_trusted_match_router_required
                            !pinRouter -> R.string.followups_trusted_match_router_off_warning
                            else -> R.string.trusted_match_router_desc
                        }
                    ),
                    checked = pinRouter,
                    enabled = !routerRequired,
                    onCheckedChange = { checked ->
                        if (checked) matchRouter = true else showNameOnlyWarning = true
                    },
                    accent = if (pinRouter) SettingsAccent.Secondary else SettingsAccent.Error
                )
                Spacer(Modifier.height(ListItemDefaults.SegmentedGap))
                ActionSettingItem(
                    shapes = ListItemDefaults.segmentedShapes(0, 1),
                    icon = chosen.kind.icon,
                    title = stringResource(R.string.profiles_selector_title),
                    summary = null,
                    onClick = { menuExpanded = true },
                    accent = SettingsAccent.Tertiary,
                    trailing = {
                        ProfileMenu(
                            expanded = menuExpanded,
                            onExpandedChange = { menuExpanded = it },
                            label = profileName(chosen),
                            profiles = profiles,
                            noneLabel = null,
                            onSelect = { id -> chosenId = id }
                        )
                    }
                )
                if (!canSave) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.followups_trusted_router_unavailable),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.error
                    )
                }
                Spacer(Modifier.height(16.dp))
                val haptics = rememberSettingsHaptics()
                Button(
                    enabled = canSave,
                    onClick = {
                        haptics.confirm()
                        state.setRule(
                            NetworkProfileRule(
                                network = TrustedNetwork(
                                    ssid = wifi.ssid,
                                    // Never fall back to the name alone when the router is redacted.
                                    bssid = if (pinRouter) WifiTrust.normalizeBssid(wifi.bssid) else null,
                                    securityType = wifi.securityType,
                                    addedAt = System.currentTimeMillis()
                                ),
                                profileId = chosen.id
                            )
                        )
                        onLinked(wifi.displayName, chosenName.orEmpty())
                    },
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(ButtonDefaults.MediumContainerHeight)
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Text(
                        stringResource(R.string.network_profiles_add),
                        modifier = Modifier.padding(start = ButtonDefaults.IconSpacing)
                    )
                }
            }
        }
    }
}
