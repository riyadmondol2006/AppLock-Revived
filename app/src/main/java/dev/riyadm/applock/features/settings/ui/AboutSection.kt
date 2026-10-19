package dev.riyadm.applock.features.settings.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Handshake
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
import dev.riyadm.applock.R
import dev.riyadm.applock.ui.icons.Github

const val DEVELOPER_URL = "https://github.com/riyadmondol2006"
const val REPOSITORY_URL = "https://github.com/riyadmondol2006/AppLock-Revived"
const val ISSUES_URL = "$REPOSITORY_URL/issues"
const val RELEASES_URL = "$REPOSITORY_URL/releases"
const val LICENSE_URL = "$REPOSITORY_URL/blob/master/LICENSE"
const val UPSTREAM_URL = "https://github.com/aload0/AppLock"

fun Context.openUrl(url: String) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(this, getString(R.string.settings_no_browser), Toast.LENGTH_SHORT).show()
    }
}

private data class VersionInfo(val name: String, val code: Long)

@Composable
fun AboutSection() {
    val context = LocalContext.current

    Column {
        SettingsSectionHeader(stringResource(R.string.settings_screen_about_title))
        AboutHeroCard(onDeveloperClick = { context.openUrl(DEVELOPER_URL) })
        Spacer(Modifier.height(12.dp))
        SettingsGroup(title = null) {
            item { shapes ->
                ActionSettingItem(
                    shapes = shapes,
                    icon = Icons.Rounded.Code,
                    title = stringResource(R.string.settings_about_source_title),
                    summary = stringResource(R.string.settings_about_source_desc),
                    external = true,
                    onClick = { context.openUrl(REPOSITORY_URL) }
                )
            }
            item { shapes ->
                ActionSettingItem(
                    shapes = shapes,
                    icon = Icons.Rounded.NewReleases,
                    title = stringResource(R.string.settings_about_releases_title),
                    summary = stringResource(R.string.settings_about_releases_desc),
                    external = true,
                    onClick = { context.openUrl(RELEASES_URL) }
                )
            }
            item { shapes ->
                ActionSettingItem(
                    shapes = shapes,
                    icon = Icons.Rounded.BugReport,
                    title = stringResource(R.string.settings_about_issue_title),
                    summary = stringResource(R.string.settings_about_issue_desc),
                    external = true,
                    onClick = { context.openUrl(ISSUES_URL) }
                )
            }
            item { shapes ->
                ActionSettingItem(
                    shapes = shapes,
                    icon = Icons.Rounded.Handshake,
                    title = stringResource(R.string.settings_about_credits_title),
                    summary = stringResource(R.string.settings_about_credits_desc),
                    external = true,
                    accent = SettingsAccent.Secondary,
                    onClick = { context.openUrl(UPSTREAM_URL) }
                )
            }
            item { shapes ->
                ActionSettingItem(
                    shapes = shapes,
                    icon = Icons.Rounded.Gavel,
                    title = stringResource(R.string.settings_about_license_title),
                    summary = stringResource(R.string.settings_about_license_desc),
                    external = true,
                    accent = SettingsAccent.Secondary,
                    onClick = { context.openUrl(LICENSE_URL) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AboutHeroCard(onDeveloperClick: () -> Unit) {
    val context = LocalContext.current
    val version = remember {
        runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            VersionInfo(info.versionName ?: "?", PackageInfoCompat.getLongVersionCode(info))
        }.getOrNull()
    }
    val appIcon: ImageBitmap? = remember {
        runCatching {
            context.packageManager.getApplicationIcon(context.packageName)
                .toBitmap(192, 192).asImageBitmap()
        }.getOrNull()
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(MaterialShapes.Cookie9Sided.toShape())
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                if (appIcon != null) {
                    Image(
                        bitmap = appIcon,
                        contentDescription = null,
                        modifier = Modifier.size(72.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(44.dp)
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.settings_about_app_name),
                style = MaterialTheme.typography.headlineSmallEmphasized,
                textAlign = TextAlign.Center
            )
            if (version != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.settings_about_version, version.name, version.code),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.settings_about_tagline),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
            )
            Spacer(Modifier.height(20.dp))
            FilledTonalButton(
                onClick = onDeveloperClick,
                shapes = ButtonDefaults.shapes(),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Icon(
                    imageVector = Github,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize)
                )
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.settings_about_developed_by))
            }
        }
    }
}
