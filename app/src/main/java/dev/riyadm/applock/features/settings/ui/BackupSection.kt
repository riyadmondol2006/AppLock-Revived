package dev.riyadm.applock.features.settings.ui

import android.content.ActivityNotFoundException
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import dev.riyadm.applock.R
import dev.riyadm.applock.data.repository.AppLockRepository
import dev.riyadm.applock.data.repository.BackupRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "BackupSection"

@Composable
fun BackupSection(
    repository: AppLockRepository,
    snackbarHostState: SnackbarHostState,
    onImported: (BackupRepository.ImportResult) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val backup = remember(repository) { BackupRepository(context.applicationContext, repository) }
    val currentOnImported by rememberUpdatedState(onImported)
    var busy by remember { mutableStateOf(false) }
    val haptics = rememberSettingsHaptics()

    fun showMessage(message: String, long: Boolean = false) {
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(
                message = message,
                withDismissAction = long,
                duration = if (long) SnackbarDuration.Long else SnackbarDuration.Short
            )
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BackupRepository.MIME_TYPE)
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val result = runCatching { withContext(Dispatchers.IO) { backup.export(uri) } }
            busy = false
            result.onSuccess {
                haptics.confirm()
                showMessage(context.getString(R.string.settings_backup_export_success))
            }.onFailure { e ->
                Log.e(TAG, "Export failed", e)
                haptics.reject()
                showMessage(
                    context.getString(
                        R.string.settings_backup_export_failed,
                        e.localizedMessage ?: e.javaClass.simpleName
                    ),
                    long = true
                )
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val result = runCatching { withContext(Dispatchers.IO) { backup.import(uri) } }
            busy = false
            result.onSuccess {
                haptics.confirm()
                currentOnImported(it)
            }
                .onFailure { e ->
                    Log.e(TAG, "Import failed", e)
                    haptics.reject()
                    val message = when {
                        e is IllegalArgumentException && e.message?.contains("newer") == true ->
                            context.getString(R.string.settings_backup_import_newer_version)

                        e is IllegalArgumentException ->
                            context.getString(R.string.settings_backup_import_invalid)

                        else -> context.getString(
                            R.string.settings_backup_import_failed,
                            e.localizedMessage ?: e.javaClass.simpleName
                        )
                    }
                    showMessage(message, long = true)
                }
        }
    }

    SettingsGroup(
        title = stringResource(R.string.settings_section_backup),
        footer = stringResource(R.string.settings_backup_footer)
    ) {
        item { shapes ->
            ActionSettingItem(
                shapes = shapes,
                icon = Icons.Rounded.FileUpload,
                title = stringResource(R.string.settings_backup_export_title),
                summary = stringResource(R.string.settings_backup_export_desc),
                enabled = !busy,
                accent = SettingsAccent.Secondary,
                onClick = {
                    try {
                        exportLauncher.launch(BackupRepository.SUGGESTED_FILE_NAME)
                    } catch (_: ActivityNotFoundException) {
                        showMessage(context.getString(R.string.settings_backup_no_file_picker), long = true)
                    }
                }
            )
        }
        item { shapes ->
            ActionSettingItem(
                shapes = shapes,
                icon = Icons.Rounded.FileDownload,
                title = stringResource(R.string.settings_backup_import_title),
                summary = stringResource(R.string.settings_backup_import_desc),
                enabled = !busy,
                accent = SettingsAccent.Secondary,
                onClick = {
                    try {
                        importLauncher.launch(
                            arrayOf(BackupRepository.MIME_TYPE, "text/*", "application/octet-stream")
                        )
                    } catch (_: ActivityNotFoundException) {
                        showMessage(context.getString(R.string.settings_backup_no_file_picker), long = true)
                    }
                }
            )
        }
    }
}
