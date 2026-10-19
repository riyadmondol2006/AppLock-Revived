package dev.riyadm.applock.features.triggerexclusions.ui

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.view.inputmethod.InputMethodManager
import androidx.core.content.getSystemService
import dev.riyadm.applock.features.applist.ui.PackageToggleViewModel

class TriggerExclusionsViewModel(application: Application) : PackageToggleViewModel(application) {

    init {
        load()
    }

    override fun readChecked(): Set<String> = repository.getTriggerExcludedApps()

    /** Keyboards, launchers and core system UI are the usual candidates, so always list them. */
    override fun alwaysVisiblePackages(): Set<String> {
        val app = getApplication<Application>()
        val result = HashSet(IMPORTANT_SYSTEM_APPS)
        app.getSystemService<InputMethodManager>()?.enabledInputMethodList
            ?.mapTo(result) { it.packageName }
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        app.packageManager.queryIntentActivities(home, PackageManager.MATCH_DEFAULT_ONLY)
            .mapTo(result) { it.activityInfo.packageName }
        return result
    }

    fun setExcluded(packageName: String, excluded: Boolean) {
        if (excluded) {
            repository.addTriggerExcludedApp(packageName)
        } else {
            repository.removeTriggerExcludedApp(packageName)
        }
        updateChecked(packageName, excluded)
    }

    fun addManualPackage(packageName: String) {
        val name = packageName.trim()
        if (isValidPackageName(name)) setExcluded(name, true)
    }

    private companion object {
        val IMPORTANT_SYSTEM_APPS = setOf(
            "com.android.chrome",
            "com.android.vending",
            "com.google.android.gms",
            "com.android.settings",
            "com.android.systemui",
            "com.android.launcher3"
        )
    }
}
