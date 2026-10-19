package dev.riyadm.applock.features.settings.ui

import android.content.ActivityNotFoundException
import android.net.Uri
import android.provider.DocumentsContract
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import dev.riyadm.applock.R
import dev.riyadm.applock.data.repository.AppLockRepository
import dev.riyadm.applock.data.repository.BackupCrypto
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

    var showExportDialog by remember { mutableStateOf(false) }
    // Password chosen for the export in progress; null exports a plain file.
    var exportPassword by remember { mutableStateOf<CharArray?>(null) }
    var exportEncrypted by rememberSaveable { mutableStateOf(false) }
    // Encrypted file waiting for its password.
    var pendingImport by remember { mutableStateOf<Uri?>(null) }
    var wrongPasswordCount by remember { mutableIntStateOf(0) }

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
        val password = exportPassword
        val wantedEncryption = exportEncrypted
        exportPassword = null
        exportEncrypted = false
        if (uri == null) {
            password?.fill('\u0000')
            return@rememberLauncherForActivityResult
        }
        if (wantedEncryption && password == null) {
            // The screen was recreated while the file picker was open and the password is gone
            // (it is never saved). Don't silently write an unencrypted file instead.
            runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
            showMessage(context.getString(R.string.backup_export_interrupted), long = true)
            return@rememberLauncherForActivityResult
        }
        busy = true
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    try {
                        backup.export(uri, password)
                    } finally {
                        password?.fill('\u0000')
                    }
                }
            }
            busy = false
            result.onSuccess {
                haptics.confirm()
                showMessage(
                    context.getString(
                        if (password != null) R.string.backup_export_success_encrypted
                        else R.string.settings_backup_export_success
                    )
                )
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

    fun runImport(uri: Uri, password: CharArray?) {
        busy = true
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    try {
                        backup.import(uri, password)
                    } finally {
                        password?.fill('\u0000')
                    }
                }
            }
            busy = false
            result.onSuccess {
                pendingImport = null
                wrongPasswordCount = 0
                haptics.confirm()
                currentOnImported(it)
            }.onFailure { e ->
                when (e) {
                    is BackupRepository.PasswordRequiredException -> {
                        wrongPasswordCount = 0
                        pendingImport = uri
                        return@onFailure
                    }

                    is BackupCrypto.WrongPasswordException -> {
                        wrongPasswordCount++
                        return@onFailure
                    }
                }
                Log.e(TAG, "Import failed", e)
                pendingImport = null
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

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) runImport(uri, null)
    }

    fun launchExport(password: CharArray?) {
        showExportDialog = false
        exportPassword = password
        exportEncrypted = password != null
        try {
            exportLauncher.launch(
                if (password != null) BackupRepository.SUGGESTED_ENCRYPTED_FILE_NAME
                else BackupRepository.SUGGESTED_FILE_NAME
            )
        } catch (_: ActivityNotFoundException) {
            exportPassword = null
            exportEncrypted = false
            password?.fill('\u0000')
            showMessage(context.getString(R.string.settings_backup_no_file_picker), long = true)
        }
    }

    if (showExportDialog) {
        ExportProtectionDialog(
            onEncrypt = { launchExport(it) },
            onExportPlain = { launchExport(null) },
            onDismiss = { showExportDialog = false }
        )
    }

    pendingImport?.let { uri ->
        ImportPasswordDialog(
            busy = busy,
            wrongPassword = wrongPasswordCount,
            onSubmit = { runImport(uri, it) },
            onDismiss = {
                pendingImport = null
                wrongPasswordCount = 0
            }
        )
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
                summary = stringResource(R.string.backup_export_desc_recommend),
                enabled = !busy,
                accent = SettingsAccent.Secondary,
                onClick = { showExportDialog = true }
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
