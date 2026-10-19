package dev.riyadm.applock.features.triggerexclusions.ui

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.riyadm.applock.core.utils.appLockRepository
import dev.riyadm.applock.features.applist.domain.AppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TriggerExclusionsViewModel : ViewModel() {
    private val _allApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val allApps: StateFlow<List<AppInfo>> = _allApps.asStateFlow()

    private val _filteredApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val filteredApps: StateFlow<List<AppInfo>> = _filteredApps.asStateFlow()

    private val _excludedApps = MutableStateFlow<Set<String>>(emptySet())
    val excludedApps: StateFlow<Set<String>> = _excludedApps.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _manualPackageName = MutableStateFlow("")
    val manualPackageName: StateFlow<String> = _manualPackageName.asStateFlow()

    fun loadApps(context: Context) {
        viewModelScope.launch {
            _isLoading.value = true

            val repository = context.appLockRepository()
            _excludedApps.value = repository.getTriggerExcludedApps()

            val apps = withContext(Dispatchers.IO) {
                getInstalledApps(context)
            }

            _allApps.value = apps
            _filteredApps.value = apps
            _isLoading.value = false
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        filterApps(query)
    }

    fun updateManualPackageName(packageName: String) {
        _manualPackageName.value = packageName
    }

    private fun filterApps(query: String) {
        _filteredApps.value = if (query.isEmpty()) {
            _allApps.value
        } else {
            _allApps.value.filter { app ->
                app.name.contains(query, ignoreCase = true) ||
                        app.packageName.contains(query, ignoreCase = true)
            }
        }
    }

    fun toggleAppExclusion(context: Context, packageName: String) {
        val repository = context.appLockRepository()
        val currentExclusions = _excludedApps.value.toMutableSet()

        if (currentExclusions.contains(packageName)) {
            repository.removeTriggerExcludedApp(packageName)
            currentExclusions.remove(packageName)
        } else {
            repository.addTriggerExcludedApp(packageName)
            currentExclusions.add(packageName)
        }

        _excludedApps.value = currentExclusions
    }

    fun addManualPackage(context: Context, packageName: String) {
        if (packageName.isNotBlank()) {
            val repository = context.appLockRepository()
            repository.addTriggerExcludedApp(packageName.trim())

            val currentExclusions = _excludedApps.value.toMutableSet()
            currentExclusions.add(packageName.trim())
            _excludedApps.value = currentExclusions

            _manualPackageName.value = ""
        }
    }

    private suspend fun getInstalledApps(context: Context): List<AppInfo> {
        val packageManager = context.packageManager
        val installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)

        return installedApps
            .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 || isImportantSystemApp(it) }
            .map { appInfo ->
                AppInfo(
                    name = packageManager.getApplicationLabel(appInfo).toString(),
                    packageName = appInfo.packageName,
                    icon = packageManager.getApplicationIcon(appInfo)
                )
            }
            .sortedBy { it.name.lowercase() }
    }

    private fun isImportantSystemApp(appInfo: ApplicationInfo): Boolean {
        val importantSystemApps = setOf(
            "com.android.chrome",
            "com.android.vending",
            "com.google.android.gms",
            "com.android.settings",
            "com.android.systemui",
            "com.android.launcher3"
        )
        return appInfo.packageName in importantSystemApps
    }
}
