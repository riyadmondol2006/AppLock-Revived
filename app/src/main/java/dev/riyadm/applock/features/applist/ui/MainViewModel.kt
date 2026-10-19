package dev.riyadm.applock.features.applist.ui

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.riyadm.applock.core.utils.appLockRepository
import dev.riyadm.applock.features.applist.domain.AppSearchManager
import dev.riyadm.applock.features.applist.domain.InstalledApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AppFilter { ALL, LOCKED, UNLOCKED, SYSTEM }

enum class AppSort { NAME, RECENT }

@Immutable
data class AppListUiState(
    val isLoading: Boolean = true,
    val lockedApps: List<InstalledApp> = emptyList(),
    val otherApps: List<InstalledApp> = emptyList(),
    val lockedCount: Int = 0,
    val filter: AppFilter = AppFilter.ALL,
    val sort: AppSort = AppSort.NAME,
    val showSystemFilter: Boolean = false,
    val query: String = "",
) {
    val isEmpty: Boolean get() = lockedApps.isEmpty() && otherApps.isEmpty()
}

private data class ListOptions(
    val filter: AppFilter = AppFilter.ALL,
    val sort: AppSort = AppSort.NAME,
    val showSystemApps: Boolean = false,
)

@OptIn(FlowPreview::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val appSearchManager = AppSearchManager(application)
    private val appLockRepository = application.appLockRepository()

    /** null while the first load is running. */
    private val allApps = MutableStateFlow<List<InstalledApp>?>(null)
    private val lockedPackages = MutableStateFlow(appLockRepository.getLockedApps())
    private val options = MutableStateFlow(
        ListOptions(showSystemApps = appLockRepository.shouldShowSystemApps())
    )

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selection = MutableStateFlow<Set<String>>(emptySet())
    val selection: StateFlow<Set<String>> = _selection.asStateFlow()

    val uiState: StateFlow<AppListUiState> = combine(
        allApps,
        lockedPackages,
        _searchQuery.debounce { if (it.isEmpty()) 0L else SEARCH_DEBOUNCE_MS },
        options,
    ) { apps, locked, query, opts ->
        buildState(apps, locked, query.trim(), opts)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = AppListUiState()
        )

    private var reloadJob: Job? = null

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.getBooleanExtra(Intent.EXTRA_REPLACING, false) == true &&
                intent.action == Intent.ACTION_PACKAGE_REMOVED
            ) return
            intent?.data?.schemeSpecificPart?.let(AppIconCache::invalidate)
            scheduleReload()
        }
    }

    init {
        loadApps(useCache = false)
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        runCatching {
            ContextCompat.registerReceiver(
                application,
                packageReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }.onFailure { Log.w(TAG, "Couldn't listen for package changes", it) }
    }

    private fun buildState(
        apps: List<InstalledApp>?,
        locked: Set<String>,
        query: String,
        opts: ListOptions,
    ): AppListUiState {
        if (apps == null) {
            return AppListUiState(
                isLoading = true,
                lockedCount = locked.size,
                filter = opts.filter,
                sort = opts.sort,
                showSystemFilter = opts.showSystemApps,
                query = query
            )
        }

        val filter = if (!opts.showSystemApps && opts.filter == AppFilter.SYSTEM) {
            AppFilter.ALL
        } else opts.filter

        val visible = apps.filter { app ->
            val isLocked = app.packageName in locked
            (isLocked || opts.showSystemApps || app.isUserFacing) &&
                    (filter != AppFilter.SYSTEM || app.isSystem) &&
                    app.matches(query)
        }
        val sorted = when (opts.sort) {
            AppSort.NAME -> visible
            AppSort.RECENT -> visible.sortedByDescending { it.firstInstallTime }
        }
        val (lockedApps, otherApps) = sorted.partition { it.packageName in locked }

        return AppListUiState(
            isLoading = false,
            lockedApps = if (filter == AppFilter.UNLOCKED) emptyList() else lockedApps,
            otherApps = if (filter == AppFilter.LOCKED) emptyList() else otherApps,
            lockedCount = apps.count { it.packageName in locked },
            filter = filter,
            sort = opts.sort,
            showSystemFilter = opts.showSystemApps,
            query = query
        )
    }

    private fun loadApps(useCache: Boolean) {
        reloadJob?.cancel()
        reloadJob = viewModelScope.launch {
            try {
                allApps.value = appSearchManager.loadInstalledApps(useCache)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load apps", e)
                if (allApps.value == null) allApps.value = emptyList()
            }
            lockedPackages.value = appLockRepository.getLockedApps()
        }
    }

    private fun scheduleReload() {
        reloadJob?.cancel()
        reloadJob = viewModelScope.launch {
            delay(PACKAGE_CHANGE_DEBOUNCE_MS)
            AppSearchManager.invalidate()
            try {
                allApps.value = appSearchManager.loadInstalledApps(useCache = false)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to reload apps", e)
            }
            lockedPackages.value = appLockRepository.getLockedApps()
        }
    }

    /** Re-reads state that other screens (settings, backup import, receivers) may have changed. */
    fun refresh() {
        lockedPackages.value = appLockRepository.getLockedApps()
        val showSystem = appLockRepository.shouldShowSystemApps()
        options.update { it.copy(showSystemApps = showSystem) }
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun setFilter(filter: AppFilter) {
        options.update { it.copy(filter = filter) }
    }

    fun setSort(sort: AppSort) {
        options.update { it.copy(sort = sort) }
    }

    fun setLocked(packageName: String, locked: Boolean) {
        if (locked) {
            appLockRepository.addLockedApp(packageName)
        } else {
            appLockRepository.removeLockedApp(packageName)
        }
        lockedPackages.value = appLockRepository.getLockedApps()
    }

    /** Locks or unlocks [packageNames], returning the packages whose state actually changed. */
    fun setLocked(packageNames: Set<String>, locked: Boolean): Set<String> {
        val current = lockedPackages.value
        val changed = if (locked) packageNames - current else packageNames intersect current
        if (changed.isEmpty()) return changed
        if (locked) {
            appLockRepository.addMultipleLockedApps(changed)
        } else {
            changed.forEach(appLockRepository::removeLockedApp)
        }
        lockedPackages.value = appLockRepository.getLockedApps()
        return changed
    }

    fun startSelection(packageName: String) {
        _selection.value = setOf(packageName)
    }

    fun setSelected(packageName: String, selected: Boolean) {
        _selection.update { if (selected) it + packageName else it - packageName }
    }

    fun selectAllVisible() {
        val state = uiState.value
        _selection.update { current ->
            current + state.lockedApps.map { it.packageName } + state.otherApps.map { it.packageName }
        }
    }

    fun clearSelection() {
        _selection.value = emptySet()
    }

    fun lockSelected(locked: Boolean): Set<String> {
        val changed = setLocked(_selection.value, locked)
        clearSelection()
        return changed
    }

    override fun onCleared() {
        runCatching { getApplication<Application>().unregisterReceiver(packageReceiver) }
        super.onCleared()
    }

    private companion object {
        const val TAG = "MainViewModel"
        const val SEARCH_DEBOUNCE_MS = 120L
        const val PACKAGE_CHANGE_DEBOUNCE_MS = 800L
    }
}
