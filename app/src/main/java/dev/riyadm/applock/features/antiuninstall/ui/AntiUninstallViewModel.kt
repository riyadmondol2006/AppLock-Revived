package dev.riyadm.applock.features.antiuninstall.ui

import android.app.Application
import androidx.lifecycle.viewModelScope
import dev.riyadm.applock.features.antiuninstall.domain.ShizukuState
import dev.riyadm.applock.features.antiuninstall.domain.UninstallBlocker
import dev.riyadm.applock.features.antiuninstall.domain.checkShizukuState
import dev.riyadm.applock.features.applist.domain.InstalledApp
import dev.riyadm.applock.features.applist.ui.PackageToggleViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

sealed interface AntiUninstallEvent {
    data object NeedsShizuku : AntiUninstallEvent
    data class ToggleFailed(val packageName: String) : AntiUninstallEvent
    data class RemovedAll(val count: Int) : AntiUninstallEvent
}

/**
 * The system is the source of truth for uninstall blocking: while Shizuku is available the
 * stored list is reconciled with the real state, so packages blocked earlier (e.g. before the
 * app's data was cleared) show up and can be released.
 */
class AntiUninstallViewModel(application: Application) : PackageToggleViewModel(application) {

    private val _shizukuState = MutableStateFlow(checkShizukuState(application))
    val shizukuState: StateFlow<ShizukuState> = _shizukuState.asStateFlow()

    private val _pending = MutableStateFlow<Set<String>>(emptySet())
    val pending: StateFlow<Set<String>> = _pending.asStateFlow()

    private val _isWorking = MutableStateFlow(false)
    val isWorking: StateFlow<Boolean> = _isWorking.asStateFlow()

    private val events = Channel<AntiUninstallEvent>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    private val binderReceived = Shizuku.OnBinderReceivedListener { refreshShizukuState() }
    private val binderDead = Shizuku.OnBinderDeadListener { refreshShizukuState() }
    private val permissionResult =
        Shizuku.OnRequestPermissionResultListener { _, _ -> refreshShizukuState() }

    init {
        runCatching {
            Shizuku.addBinderReceivedListenerSticky(binderReceived)
            Shizuku.addBinderDeadListener(binderDead)
            Shizuku.addRequestPermissionResultListener(permissionResult)
        }
        load()
    }

    override fun readChecked(): Set<String> = repository.getAntiUninstallApps()

    override suspend fun onAppsLoaded(apps: List<InstalledApp>) {
        if (_shizukuState.value == ShizukuState.READY) syncWithSystem()
    }

    fun refreshShizukuState() {
        val previous = _shizukuState.value
        val current = checkShizukuState(getApplication())
        _shizukuState.value = current
        if (current == ShizukuState.READY && previous != ShizukuState.READY && allApps.value != null) {
            viewModelScope.launch { syncWithSystem() }
        }
    }

    private suspend fun syncWithSystem() {
        val apps = allApps.value ?: return
        val blocked = withContext(Dispatchers.IO) {
            buildSet {
                for (app in apps) {
                    when (UninstallBlocker.isBlocked(app.packageName)) {
                        true -> add(app.packageName)
                        null -> return@withContext null
                        false -> Unit
                    }
                }
            }
        } ?: return
        val installed = apps.mapTo(HashSet()) { it.packageName }
        val stored = repository.getAntiUninstallApps()
        // Keep manually added, not-installed packages; mirror the system for everything else.
        val merged = stored.filterNotTo(HashSet()) { it in installed } + blocked
        (stored - merged).forEach(repository::removeAntiUninstallApp)
        (merged - stored).forEach(repository::addAntiUninstallApp)
        _checked.value = merged
    }

    fun setProtected(packageName: String, protect: Boolean) {
        if (_shizukuState.value != ShizukuState.READY) {
            refreshShizukuState()
            if (_shizukuState.value != ShizukuState.READY) {
                events.trySend(AntiUninstallEvent.NeedsShizuku)
                return
            }
        }
        if (packageName in _pending.value) return
        _pending.update { it + packageName }
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { UninstallBlocker.setBlocked(packageName, protect) }
            if (ok) {
                if (protect) {
                    repository.addAntiUninstallApp(packageName)
                } else {
                    repository.removeAntiUninstallApp(packageName)
                }
                updateChecked(packageName, protect)
            } else {
                val installed = allApps.value?.any { it.packageName == packageName } == true
                if (!protect && !installed) {
                    // Nothing to release for a package that isn't installed
                    repository.removeAntiUninstallApp(packageName)
                    updateChecked(packageName, false)
                } else {
                    refreshShizukuState()
                    events.send(AntiUninstallEvent.ToggleFailed(packageName))
                }
            }
            _pending.update { it - packageName }
        }
    }

    fun addManualPackage(packageName: String) {
        val name = packageName.trim()
        if (isValidPackageName(name)) setProtected(name, true)
    }

    /** Releases every package on the device that is blocked from uninstalling. */
    fun removeAllProtection() {
        if (_shizukuState.value != ShizukuState.READY) {
            events.trySend(AntiUninstallEvent.NeedsShizuku)
            return
        }
        viewModelScope.launch {
            _isWorking.value = true
            val candidates = buildSet {
                allApps.value?.forEach { add(it.packageName) }
                addAll(repository.getAntiUninstallApps())
            }
            val released = withContext(Dispatchers.IO) {
                candidates.count { pkg ->
                    UninstallBlocker.isBlocked(pkg) == true && UninstallBlocker.setBlocked(pkg, false)
                }
            }
            repository.getAntiUninstallApps().forEach(repository::removeAntiUninstallApp)
            _checked.value = emptySet()
            _isWorking.value = false
            events.send(AntiUninstallEvent.RemovedAll(released))
        }
    }

    override fun onCleared() {
        runCatching {
            Shizuku.removeBinderReceivedListener(binderReceived)
            Shizuku.removeBinderDeadListener(binderDead)
            Shizuku.removeRequestPermissionResultListener(permissionResult)
        }
        super.onCleared()
    }
}
