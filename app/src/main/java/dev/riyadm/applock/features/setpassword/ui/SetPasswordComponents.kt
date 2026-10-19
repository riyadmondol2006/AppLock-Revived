package dev.riyadm.applock.features.setpassword.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Password
import androidx.compose.material.icons.rounded.Pattern
import androidx.compose.material.icons.rounded.Pin
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.riyadm.applock.R
import dev.riyadm.applock.data.repository.PreferencesRepository

enum class LockKind(val prefValue: String, val icon: ImageVector) {
    PIN(PreferencesRepository.LOCK_TYPE_PIN, Icons.Rounded.Pin),
    PATTERN(PreferencesRepository.LOCK_TYPE_PATTERN, Icons.Rounded.Pattern),
    PASSWORD(PreferencesRepository.LOCK_TYPE_PASSWORD, Icons.Rounded.Password);

    companion object {
        fun fromPref(value: String): LockKind = entries.firstOrNull { it.prefValue == value } ?: PIN
    }
}

/**
 * Plays a decaying horizontal spring wobble; read the value in a layout/draw lambda.
 */
suspend fun Animatable<Float, *>.shake() {
    snapTo(0f)
    animateTo(
        targetValue = 0f,
        animationSpec = spring(dampingRatio = 0.22f, stiffness = 1200f),
        initialVelocity = 700f
    )
}

/**
 * Connected button group to pick PIN / Pattern / Password.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LockTypeSwitcher(
    selected: LockKind,
    onSelect: (LockKind) -> Unit,
    modifier: Modifier = Modifier
) {
    val options = LockKind.entries
    val interactions = remember { options.map { MutableInteractionSource() } }
    val labels = options.map {
        stringResource(
            when (it) {
                LockKind.PIN -> R.string.setup_type_pin
                LockKind.PATTERN -> R.string.setup_type_pattern
                LockKind.PASSWORD -> R.string.setup_type_password
            }
        )
    }
    ButtonGroup(
        overflowIndicator = { state -> ButtonGroupDefaults.OverflowIndicator(menuState = state) },
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
    ) {
        options.forEachIndexed { index, kind ->
            customItem(
                buttonGroupContent = {
                    ToggleButton(
                        checked = kind == selected,
                        onCheckedChange = { onSelect(kind) },
                        interactionSource = interactions[index],
                        shapes = when (index) {
                            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                            options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .animateWidth(interactions[index])
                            .height(48.dp)
                            .semantics { role = Role.RadioButton }
                    ) {
                        Icon(
                            kind.icon,
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize)
                        )
                        Text(
                            labels[index],
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            modifier = Modifier.padding(start = ButtonDefaults.IconSpacing)
                        )
                    }
                },
                menuContent = { state ->
                    DropdownMenuItem(
                        text = { Text(labels[index]) },
                        onClick = {
                            state.dismiss()
                            onSelect(kind)
                        }
                    )
                }
            )
        }
    }
}
