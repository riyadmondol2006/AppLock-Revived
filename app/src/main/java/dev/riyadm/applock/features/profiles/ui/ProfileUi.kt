package dev.riyadm.applock.features.profiles.ui

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Password
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Work
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import dev.riyadm.applock.R
import dev.riyadm.applock.data.repository.LockProfile
import dev.riyadm.applock.data.repository.ProfileKind
import dev.riyadm.applock.data.repository.SecurityLevel

private fun ProfileKind.defaultNameRes(): Int = when (this) {
    ProfileKind.HOME -> R.string.profiles_name_home
    ProfileKind.WORK -> R.string.profiles_name_work
    ProfileKind.GUEST -> R.string.profiles_name_guest
    ProfileKind.CUSTOM -> R.string.profiles_name_custom
}

fun LockProfile.displayName(context: Context): String =
    customName ?: context.getString(kind.defaultNameRes())

@Composable
fun profileName(profile: LockProfile): String =
    profile.customName ?: stringResource(profile.kind.defaultNameRes())

val ProfileKind.icon: ImageVector
    get() = when (this) {
        ProfileKind.HOME -> Icons.Rounded.Home
        ProfileKind.WORK -> Icons.Rounded.Work
        ProfileKind.GUEST -> Icons.Rounded.Person
        ProfileKind.CUSTOM -> Icons.AutoMirrored.Rounded.Label
    }

val SecurityLevel.icon: ImageVector
    get() = when (this) {
        SecurityLevel.DEFAULT -> Icons.Rounded.Tune
        SecurityLevel.BIOMETRIC_ONLY -> Icons.Rounded.Fingerprint
        SecurityLevel.CREDENTIAL_ONLY -> Icons.Rounded.Password
        SecurityLevel.TWO_STEP -> Icons.Rounded.VerifiedUser
    }

val SecurityLevel.titleRes: Int
    get() = when (this) {
        SecurityLevel.DEFAULT -> R.string.policy_level_default
        SecurityLevel.BIOMETRIC_ONLY -> R.string.policy_level_biometric
        SecurityLevel.CREDENTIAL_ONLY -> R.string.policy_level_credential
        SecurityLevel.TWO_STEP -> R.string.policy_level_two_step
    }

val SecurityLevel.descriptionRes: Int
    get() = when (this) {
        SecurityLevel.DEFAULT -> R.string.policy_level_default_desc
        SecurityLevel.BIOMETRIC_ONLY -> R.string.policy_level_biometric_desc
        SecurityLevel.CREDENTIAL_ONLY -> R.string.policy_level_credential_desc
        SecurityLevel.TWO_STEP -> R.string.policy_level_two_step_desc
    }
