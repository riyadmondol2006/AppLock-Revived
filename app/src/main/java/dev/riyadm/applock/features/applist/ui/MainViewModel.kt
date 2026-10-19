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
import dev.riyadm.applock.data.repository.LockProfile
import dev.riyadm.applock.data.repository.LockProfilesState
import dev.riyadm.applock.data.repository.SecurityLevel
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
import kotlinx.coroutines.withContext

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
    private val _lockedPackages = MutableStateFlow(appLockRepository.getLockedApps())
    val lockedPackages: StateFlow<Set<String>> = _lockedPackages.asStateFlow()
    private val options = MutableStateFlow(
        ListOptions(showSystemApps = appLockRepository.shouldShowSystemApps())
    )

    /** Lock profiles; the active profile's apps are [lockedPackages]. */
    val profiles: StateFlow<LockProfilesState> = appLockRepository.profiles.state

    /** Apps with their own security level. */
    val securityLevels: StateFlow<Map<String, SecurityLevel>> = appLockRepository.appPolicies.levels

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selection = MutableStateFlow<Set<String>>(emptySet())
    val selection: StateFlow<Set<String>> = _selection.asStateFlow()

    val uiState: StateFlow<AppListUiState> = combine(
        allApps,
        _lockedPackages,
        options,
    ) { apps, locked, opts ->
        buildState(apps, locked, opts)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = AppListUiState()
        )

    /**
     * Search results don't depend on the locked set, so toggling a result only changes that
     * row's switch instead of rebuilding the list.
     */
    val searchResults: StateFlow<List<InstalledApp>> = combine(
        allApps,
        _searchQuery.debounce { if (it.isEmpty()) 0L else SEARCH_DEBOUNCE_MS },
        options,
    ) { apps, query, opts ->
        val trimmed = query.trim()
        if (apps == null || trimmed.isEmpty()) {
            emptyList()
        } else {
            val locked = _lockedPackages.value
            apps.filter { app ->
                (opts.showSystemApps || app.isUserFacing || app.packageName in locked) &&
                        app.matches(trimmed)
            }
        }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = emptyList()
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
        // Profile switches and other writers (receivers, backup import) change the locked set.
        viewModelScope.launch {
            appLockRepository.profiles.state.collect { _lockedPackages.value = it.active.lockedApps }
        }
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
        opts: ListOptions,
    ): AppListUiState {
        if (apps == null) {
            return AppListUiState(
                isLoading = true,
                lockedCount = locked.size,
                filter = opts.filter,
                sort = opts.sort,
                showSystemFilter = opts.showSystemApps
            )
        }

        val filter = if (!opts.showSystemApps && opts.filter == AppFilter.SYSTEM) {
            AppFilter.ALL
        } else opts.filter

        val visible = apps.filter { app ->
            val isLocked = app.packageName in locked
            (isLocked || opts.showSystemApps || app.isUserFacing) &&
                    (filter != AppFilter.SYSTEM || app.isSystem)
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
            showSystemFilter = opts.showSystemApps
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
            _lockedPackages.value = appLockRepository.getLockedApps()
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
            _lockedPackages.value = appLockRepository.getLockedApps()
        }
    }

    /** Re-reads state that other screens (settings, backup import, receivers) may have changed. */
    fun refresh() {
        _lockedPackages.value = appLockRepository.getLockedApps()
        val showSystem = appLockRepository.shouldShowSystemApps()
        options.update { it.copy(showSystemApps = showSystem) }
    }

    fun setShowSystemApps(show: Boolean) {
        appLockRepository.setShowSystemApps(show)
        options.update { it.copy(showSystemApps = show) }
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
        _lockedPackages.value = appLockRepository.getLockedApps()
    }

    /** Locks or unlocks [packageNames], returning the packages whose state actually changed. */
    fun setLocked(packageNames: Set<String>, locked: Boolean): Set<String> {
        val current = _lockedPackages.value
        val changed = if (locked) packageNames - current else packageNames intersect current
        if (changed.isEmpty()) return changed
        if (locked) {
            appLockRepository.addMultipleLockedApps(changed)
        } else {
            changed.forEach(appLockRepository::removeLockedApp)
        }
        _lockedPackages.value = appLockRepository.getLockedApps()
        return changed
    }

    /** Switches the active profile. Only reachable from the authenticated app UI. */
    suspend fun switchProfile(profile: LockProfile): Boolean {
        val switched = withContext(Dispatchers.IO) { appLockRepository.switchProfile(profile.id) }
        _lockedPackages.value = appLockRepository.getLockedApps()
        return switched
    }

    fun setSecurityLevel(packageName: String, level: SecurityLevel) {
        viewModelScope.launch(Dispatchers.IO) { appLockRepository.appPolicies.setLevel(packageName, level) }
    }

    fun setProfileMembership(profileId: String, packageName: String, member: Boolean) {
        appLockRepository.setProfileMembership(profileId, packageName, member)
        _lockedPackages.value = appLockRepository.getLockedApps()
    }

    /**
     * Locks every app shown by default (user apps and system apps with a launcher icon), except
     * App Lock itself and the home screen. Returns the packages that were newly locked.
     */
    suspend fun lockAllUserApps(): Set<String> {
        val apps = allApps.value ?: return emptySet()
        val candidates = withContext(Dispatchers.Default) {
            val excluded = homePackages() + getApplication<Application>().packageName
            apps.asSequence()
                .filter { it.isUserFacing && it.packageName !in excluded }
                .map { it.packageName }
                .toSet()
        }
        return setLocked(candidates, true)
    }

    private fun homePackages(): Set<String> = runCatching {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        getApplication<Application>().packageManager
            .queryIntentActivities(intent, 0)
            .mapNotNull { it.activityInfo?.packageName }
            .toSet()
    }.getOrDefault(emptySet())

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
