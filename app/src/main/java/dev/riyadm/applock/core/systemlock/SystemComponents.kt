package dev.riyadm.applock.core.systemlock

import android.content.Intent

/**
 * Knowledge about the system components the "Lock system components" settings can protect, kept
 * free of Android framework state so the matching rules can be unit-tested.
 *
 * Two kinds of targets exist:
 *  - the system **pickers** (photo picker, file picker): real packages, so the existing lock
 *    machinery of every backend treats them like any other locked app;
 *  - the **quick settings** panel: not a package at all. It is represented by [QUICK_SETTINGS_KEY],
 *    a pseudo package name that only the accessibility backend can raise and unlock.
 */
object SystemComponents {

    /** Pseudo package the lock overlay, unlock state and unlock history use for the quick settings guard. */
    const val QUICK_SETTINGS_KEY = "applock.system.quick_settings"

    const val SYSTEMUI_PACKAGE = "com.android.systemui"
    const val SETTINGS_PACKAGE = "com.android.settings"

    /**
     * Packages that show the system photo picker and the document / file picker.
     *
     * - MediaProvider mainline module: the Photo Picker (`PhotoPickerActivity`, Android 13+, also
     *   backported to 11/12 on Google builds). `com.google.…` on Pixel/GMS devices, `com.android.…`
     *   on AOSP builds. The same package also hosts the "allow this app to modify / delete this
     *   photo" confirmation dialogs, so those ask for authentication too.
     * - DocumentsUI: the storage access framework picker used by `ACTION_OPEN_DOCUMENT`,
     *   `ACTION_GET_CONTENT`, `ACTION_CREATE_DOCUMENT` and `ACTION_OPEN_DOCUMENT_TREE`; also the
     *   "Files" app on Pixel devices.
     * - OEM file managers that replace DocumentsUI as the picker.
     *
     * Wrong or missing entries are harmless: a package that isn't installed is never in front.
     */
    val PICKER_PACKAGES: Set<String> = setOf(
        "com.google.android.providers.media.module",
        "com.android.providers.media.module",
        "com.google.android.documentsui",
        "com.android.documentsui",
        "com.sec.android.app.myfiles",
        "com.android.fileexplorer",
        "com.mi.android.globalFileexplorer",
        "com.huawei.hidisk",
        "com.coloros.filemanager",
    )

    /** Intent actions that open a system picker; used to recognise pickers AppLock itself opens. */
    private val PICKER_ACTIONS = setOf(
        Intent.ACTION_OPEN_DOCUMENT,
        Intent.ACTION_CREATE_DOCUMENT,
        Intent.ACTION_OPEN_DOCUMENT_TREE,
        Intent.ACTION_GET_CONTENT,
        Intent.ACTION_PICK,
        "android.provider.action.PICK_IMAGES",
    )

    fun isPickerPackage(packageName: String): Boolean = packageName in PICKER_PACKAGES

    fun isPickerAction(action: String?): Boolean = action != null && action in PICKER_ACTIONS

    /** True for the pseudo packages, which have no installed app, label or icon. */
    fun isPseudoPackage(packageName: String): Boolean = packageName == QUICK_SETTINGS_KEY

    /**
     * Whether [packageName] is locked because of a system-component setting. The quick settings
     * guard also needs the accessibility backend, which is the only one that can see the panel.
     */
    fun isGuarded(
        packageName: String,
        lockPickers: Boolean,
        lockQuickSettings: Boolean,
        accessibilityBackend: Boolean,
    ): Boolean = when {
        packageName == QUICK_SETTINGS_KEY -> lockQuickSettings && accessibilityBackend
        else -> lockPickers && isPickerPackage(packageName)
    }

    // --- Quick settings detection (accessibility backend) -----------------------------------

    /**
     * View ids that only exist while the quick settings tiles are on screen. Matched on the
     * resource name without the package, because OEM builds change the package but rarely the id.
     */
    private val QS_PANEL_VIEW_IDS = setOf(
        "quick_qs_panel",
        "quick_settings_panel",
        "qs_tile_layout",
        "qs_panel",
    )

    /** Settings panel (`Settings.Panel.ACTION_INTERNET_CONNECTIVITY` & co.) title view. */
    const val SETTINGS_PANEL_TITLE_ID = "$SETTINGS_PACKAGE:id/panel_title"

    /** Full resource names to look up in the SystemUI windows. */
    val QS_PANEL_FULL_VIEW_IDS: List<String> = QS_PANEL_VIEW_IDS.map { "$SYSTEMUI_PACKAGE:id/$it" }

    /**
     * SystemUI string resources of the tiles that change connectivity, resolved against the
     * installed SystemUI so the labels match the user's language (and the OEM's naming).
     */
    val PROTECTED_TILE_LABEL_RESOURCES = listOf(
        "quick_settings_internet_label",
        "quick_settings_wifi_label",
        "quick_settings_bluetooth_label",
        "quick_settings_airplane_mode_label",
        "quick_settings_cellular_detail_title",
        "quick_settings_hotspot_label",
        "quick_settings_nfc_label",
        "quick_settings_location_label",
    )

    /** Fallback tile labels, matched when the SystemUI resources can't be read. */
    val ENGLISH_TILE_LABELS = listOf(
        "Internet",
        "Wi-Fi",
        "WiFi",
        "Bluetooth",
        "Airplane mode",
        "Flight mode",
        "Mobile data",
        "Hotspot",
        "NFC",
        "Location",
    )

    /**
     * Whether a node's text or content description names one of the [labels]. Tiles announce
     * themselves as "<label>, <state>", so the label has to be at the start; this keeps a
     * notification that merely mentions "Bluetooth" from being treated as the tile.
     */
    fun startsWithLabel(text: CharSequence?, labels: Collection<String>): Boolean {
        val value = text?.toString()?.trim().orEmpty()
        if (value.isEmpty()) return false
        return labels.any { label ->
            label.isNotBlank() && value.startsWith(label.trim(), ignoreCase = true)
        }
    }

    /** Distinct, non-blank labels from the resolved SystemUI strings plus the English fallback. */
    fun mergeLabels(resolved: Collection<String?>): List<String> =
        (resolved.filterNotNull() + ENGLISH_TILE_LABELS)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
}

/**
 * Remembers that AppLock itself just opened a system picker (backup import / export), so that
 * picker isn't locked: the user is already inside AppLock, and locking it would also break the
 * file pick the app is waiting for.
 */
object HostedPickerTracker {
    /** How long after launching a picker the first appearance of a picker is attributed to AppLock. */
    const val WINDOW_MS = 15_000L

    @Volatile
    private var launchedAt = Long.MIN_VALUE

    fun markLaunched(now: Long) {
        launchedAt = now
    }

    fun isHosted(now: Long): Boolean = launchedAt != Long.MIN_VALUE && now - launchedAt in 0..WINDOW_MS

    internal fun reset() {
        launchedAt = Long.MIN_VALUE
    }
}
