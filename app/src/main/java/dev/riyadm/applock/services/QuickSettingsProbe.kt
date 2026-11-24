package dev.riyadm.applock.services

import android.content.Context
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import dev.riyadm.applock.core.systemlock.SystemComponents
import dev.riyadm.applock.core.utils.LogUtils
import java.util.Locale

/**
 * Tells the accessibility service whether the quick settings tiles that change connectivity
 * (Internet / Wi-Fi, Bluetooth, airplane mode, mobile data, hotspot, NFC, location) are on
 * screen right now.
 *
 * Android gives accessibility services no way to veto a tap on another app's view, so the tile
 * can't be intercepted: by the time a click event arrives the toggle has already flipped. The
 * guard therefore works one step earlier, on the panel itself: while the tiles are visible, the
 * lock overlay covers the screen, so no tile can be tapped before authentication.
 *
 * Three signals count as "tiles are visible", all read from nodes that are visible to the user:
 *  1. a SystemUI window that contains a quick settings panel view id ([SystemComponents.QS_PANEL_FULL_VIEW_IDS]);
 *  2. a SystemUI window that contains a clickable node announcing a protected tile by its label;
 *  3. the Settings connectivity panel (`Settings.Panel.ACTION_INTERNET_CONNECTIVITY` & co.),
 *     recognised by its panel title.
 *
 * The result is cached for a moment: the service asks on every window scan and a probe costs a
 * handful of binder calls into SystemUI.
 */
internal class QuickSettingsProbe(private val context: Context) {

    private var labels: List<String> = SystemComponents.mergeLabels(emptyList())
    private var labelsLoadedFor = ""
    private var labelsLoadedAt = Long.MIN_VALUE

    private var cachedAt = Long.MIN_VALUE
    private var cachedResult = false

    /** True when the last positive probe was the Settings panel rather than the SystemUI shade. */
    var settingsPanelShown = false
        private set

    /** Forget the cached answer, e.g. when the feature is switched off. */
    fun invalidate() {
        cachedAt = Long.MIN_VALUE
        cachedResult = false
        settingsPanelShown = false
    }

    fun isShown(windows: List<AccessibilityWindowInfo>): Boolean {
        val now = SystemClock.uptimeMillis()
        if (cachedAt != Long.MIN_VALUE && now - cachedAt < CACHE_MS) return cachedResult
        cachedResult = try {
            probe(windows, now)
        } catch (e: Exception) {
            LogUtils.d(TAG, "Quick settings probe failed: ${e.message}")
            false
        }
        cachedAt = now
        return cachedResult
    }

    private fun probe(windows: List<AccessibilityWindowInfo>, now: Long): Boolean {
        refreshLabels(now)
        settingsPanelShown = false
        for (window in windows) {
            val root = window.root ?: continue
            when (root.packageName?.toString()) {
                SystemComponents.SYSTEMUI_PACKAGE -> if (systemUiShowsTiles(root)) return true

                SystemComponents.SETTINGS_PACKAGE ->
                    if (window.type == AccessibilityWindowInfo.TYPE_APPLICATION && isSettingsPanel(root)) {
                        settingsPanelShown = true
                        return true
                    }
            }
        }
        return false
    }

    /**
     * Walks the SystemUI window by hand. On Android 16+ the shade is a Compose scene and
     * findAccessibilityNodeInfosByViewId / ByText come back empty for it even though the nodes
     * are there, so the search is a bounded depth-first walk over the visible nodes.
     */
    private fun systemUiShowsTiles(root: AccessibilityNodeInfo): Boolean {
        var budget = MAX_NODES
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.add(root)
        while (stack.isNotEmpty() && budget-- > 0) {
            val node = stack.removeLast()
            if (!node.isVisibleToUser) continue
            val id = node.viewIdResourceName
            if (id != null && id in SystemComponents.QS_PANEL_FULL_VIEW_IDS) {
                LogUtils.d(TAG, "Quick settings tiles visible (view id $id)")
                return true
            }
            if (SystemComponents.startsWithLabel(node.contentDescription, labels) ||
                SystemComponents.startsWithLabel(node.text, labels)
            ) {
                // A bare label also appears in the status bar and notifications; only a tile counts.
                if (node.isClickable || node.parent?.isClickable == true) {
                    LogUtils.d(TAG, "Quick settings tile visible ('${node.contentDescription ?: node.text}')")
                    return true
                }
            }
            for (i in node.childCount - 1 downTo 0) node.getChild(i)?.let(stack::add)
        }
        return false
    }

    private fun isSettingsPanel(root: AccessibilityNodeInfo): Boolean =
        root.findAccessibilityNodeInfosByViewId(SystemComponents.SETTINGS_PANEL_TITLE_ID).any { title ->
            title.isVisibleToUser && SystemComponents.startsWithLabel(title.text, labels)
        }

    /** Tile labels come from the installed SystemUI so they match the user's language. */
    private fun refreshLabels(now: Long) {
        val language = Locale.getDefault().toLanguageTag()
        if (language == labelsLoadedFor && now - labelsLoadedAt < LABEL_CACHE_MS) return
        labels = SystemComponents.mergeLabels(loadSystemUiLabels())
        labelsLoadedFor = language
        labelsLoadedAt = now
    }

    private fun loadSystemUiLabels(): List<String?> = try {
        val resources = context.packageManager.getResourcesForApplication(SystemComponents.SYSTEMUI_PACKAGE)
        SystemComponents.PROTECTED_TILE_LABEL_RESOURCES.map { name ->
            val id = resources.getIdentifier(name, "string", SystemComponents.SYSTEMUI_PACKAGE)
            if (id == 0) null else runCatching { resources.getString(id) }.getOrNull()
        }
    } catch (e: Exception) {
        LogUtils.d(TAG, "Unable to read SystemUI labels: ${e.message}")
        emptyList()
    }

    private companion object {
        const val TAG = "QuickSettingsProbe"
        const val CACHE_MS = 80L
        const val MAX_NODES = 600
        const val LABEL_CACHE_MS = 10 * 60_000L
    }
}
