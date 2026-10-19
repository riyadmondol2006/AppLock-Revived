package dev.riyadm.applock.features.applist.ui

import android.app.Application
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.riyadm.applock.core.utils.appLockRepository
import dev.riyadm.applock.data.repository.AppLockRepository
import dev.riyadm.applock.features.applist.domain.AppSearchManager
import dev.riyadm.applock.features.applist.domain.InstalledApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
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

@Immutable
data class PackageToggleUiState(
    val isLoading: Boolean = true,
    val checkedApps: List<InstalledApp> = emptyList(),
    val otherApps: List<InstalledApp> = emptyList(),
    /** Checked packages that aren't installed (added by name). */
    val manualPackages: List<String> = emptyList(),
    val showSystemApps: Boolean = false,
)

/**
 * Shared state for screens that toggle a set of packages on / off (trigger exclusions,
 * prevent uninstall). Lists are built on [Dispatchers.Default]; the checked set is exposed
 * separately so rows can derive their own state.
 */
@OptIn(FlowPreview::class)
abstract class PackageToggleViewModel(application: Application) : AndroidViewModel(application) {
    protected val repository: AppLockRepository = application.appLockRepository()
    protected val appSearchManager = AppSearchManager(application)

    protected val allApps = MutableStateFlow<List<InstalledApp>?>(null)
    protected val _checked = MutableStateFlow<Set<String>>(emptySet())
    val checked: StateFlow<Set<String>> = _checked.asStateFlow()

    private val showSystemApps = MutableStateFlow(repository.shouldShowSystemApps())
    private val searchQuery = MutableStateFlow("")

    /** Packages that are listed even when system apps are hidden. */
    protected open fun alwaysVisiblePackages(): Set<String> = emptySet()

    protected abstract fun readChecked(): Set<String>

    private var alwaysVisible: Set<String> = emptySet()

    val uiState: StateFlow<PackageToggleUiState> =
        combine(allApps, _checked, showSystemApps) { apps, checked, showSystem ->
            if (apps == null) return@combine PackageToggleUiState(showSystemApps = showSystem)
            val installed = apps.mapTo(HashSet(apps.size)) { it.packageName }
            val (checkedApps, others) = apps.partition { it.packageName in checked }
            PackageToggleUiState(
                isLoading = false,
                checkedApps = checkedApps,
                otherApps = others.filter { isListed(it, showSystem) },
                manualPackages = checked.filterNot { it in installed }.sorted(),
                showSystemApps = showSystem
            )
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), PackageToggleUiState())

    val searchResults: StateFlow<List<InstalledApp>> = combine(
        allApps,
        searchQuery.debounce { if (it.isEmpty()) 0L else 120L },
        showSystemApps
    ) { apps, query, showSystem ->
        val trimmed = query.trim()
        if (apps == null || trimmed.isEmpty()) emptyList()
        else {
            val checked = _checked.value
            apps.filter {
                (it.packageName in checked || isListed(it, showSystem)) && it.matches(trimmed)
            }
        }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    private fun isListed(app: InstalledApp, showSystem: Boolean) =
        showSystem || app.isUserFacing || app.packageName in alwaysVisible

    /** Call from the subclass' init block once its own fields are initialised. */
    protected fun load() {
        viewModelScope.launch {
            _checked.value = readChecked()
            try {
                val apps = appSearchManager.loadInstalledApps()
                alwaysVisible = withContext(Dispatchers.IO) {
                    runCatching { alwaysVisiblePackages() }.getOrDefault(emptySet())
                }
                allApps.value = apps
                onAppsLoaded(apps)
            } catch (e: Exception) {
                Log.e("PackageToggleViewModel", "Failed to load apps", e)
                allApps.value = emptyList()
            }
        }
    }

    protected open suspend fun onAppsLoaded(apps: List<InstalledApp>) = Unit

    fun setShowSystemApps(show: Boolean) {
        showSystemApps.value = show
    }

    fun onSearchQueryChange(query: String) {
        searchQuery.value = query
    }

    protected fun updateChecked(packageName: String, checked: Boolean) {
        _checked.update { if (checked) it + packageName else it - packageName }
    }

    companion object {
        private val PACKAGE_NAME_REGEX = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$")

        fun isValidPackageName(name: String): Boolean = PACKAGE_NAME_REGEX.matches(name.trim())
    }
}
