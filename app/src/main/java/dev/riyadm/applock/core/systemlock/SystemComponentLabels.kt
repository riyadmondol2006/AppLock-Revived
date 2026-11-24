package dev.riyadm.applock.core.systemlock

import android.content.Context
import dev.riyadm.applock.R

/** Display name of a pseudo package such as [SystemComponents.QUICK_SETTINGS_KEY], or null for a real one. */
fun Context.systemComponentLabel(packageName: String): String? =
    if (SystemComponents.isPseudoPackage(packageName)) {
        getString(R.string.system_lock_quick_settings_label)
    } else {
        null
    }
