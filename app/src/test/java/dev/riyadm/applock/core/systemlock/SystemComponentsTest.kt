package dev.riyadm.applock.core.systemlock

import android.content.Intent
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Test

class SystemComponentsTest {

    @After
    fun tearDown() {
        HostedPickerTracker.reset()
    }

    @Test
    fun pickerPackages_coverPhotoPickerAndDocumentsUiOnPixelAndAosp() {
        listOf(
            "com.google.android.providers.media.module",
            "com.android.providers.media.module",
            "com.google.android.documentsui",
            "com.android.documentsui",
            "com.sec.android.app.myfiles",
        ).forEach { assertThat(SystemComponents.isPickerPackage(it)).isTrue() }
    }

    @Test
    fun ordinaryAppsAndSystemUiAreNotPickers() {
        listOf(
            "com.whatsapp",
            "com.google.android.apps.photos",
            "com.android.systemui",
            "com.android.settings",
            "dev.riyadm.applock",
            "",
        ).forEach { assertThat(SystemComponents.isPickerPackage(it)).isFalse() }
    }

    @Test
    fun pickerToggle_onlyGuardsPickerPackages() {
        val photoPicker = "com.google.android.providers.media.module"
        assertThat(guarded(photoPicker, pickers = true)).isTrue()
        assertThat(guarded(photoPicker, pickers = false)).isFalse()
        assertThat(guarded("com.whatsapp", pickers = true, quickSettings = true)).isFalse()
    }

    @Test
    fun pickerGuard_worksWithEveryBackend() {
        val documentsUi = "com.android.documentsui"
        assertThat(guarded(documentsUi, pickers = true, accessibility = false)).isTrue()
        assertThat(guarded(documentsUi, pickers = true, accessibility = true)).isTrue()
    }

    @Test
    fun quickSettingsGuard_needsItsToggleAndTheAccessibilityBackend() {
        val key = SystemComponents.QUICK_SETTINGS_KEY
        assertThat(guarded(key, quickSettings = true, accessibility = true)).isTrue()
        assertThat(guarded(key, quickSettings = true, accessibility = false)).isFalse()
        assertThat(guarded(key, quickSettings = false, accessibility = true)).isFalse()
        // The pickers toggle never turns the panel guard on.
        assertThat(guarded(key, pickers = true, accessibility = true)).isFalse()
    }

    @Test
    fun pseudoPackage_isOnlyTheQuickSettingsKey() {
        assertThat(SystemComponents.isPseudoPackage(SystemComponents.QUICK_SETTINGS_KEY)).isTrue()
        assertThat(SystemComponents.isPseudoPackage("com.android.systemui")).isFalse()
        assertThat(SystemComponents.isPickerPackage(SystemComponents.QUICK_SETTINGS_KEY)).isFalse()
    }

    @Test
    fun pickerActions_includeEveryWayAnAppOpensASystemPicker() {
        listOf(
            Intent.ACTION_OPEN_DOCUMENT,
            Intent.ACTION_CREATE_DOCUMENT,
            Intent.ACTION_OPEN_DOCUMENT_TREE,
            Intent.ACTION_GET_CONTENT,
            "android.provider.action.PICK_IMAGES",
        ).forEach { assertThat(SystemComponents.isPickerAction(it)).isTrue() }
        assertThat(SystemComponents.isPickerAction(Intent.ACTION_VIEW)).isFalse()
        assertThat(SystemComponents.isPickerAction(null)).isFalse()
    }

    @Test
    fun tileLabel_mustStartTheDescription() {
        val labels = SystemComponents.mergeLabels(listOf("Internet", "Bluetooth"))
        assertThat(SystemComponents.startsWithLabel("Internet, Wi-Fi off.", labels)).isTrue()
        assertThat(SystemComponents.startsWithLabel("  bluetooth,Off", labels)).isTrue()
        assertThat(SystemComponents.startsWithLabel("AIRPLANE MODE, Off", labels)).isTrue()
        // A notification that only mentions a tile is not the tile.
        assertThat(SystemComponents.startsWithLabel("Pixel Buds, Bluetooth connected", labels)).isFalse()
        assertThat(SystemComponents.startsWithLabel("Flashlight, Off", labels)).isFalse()
    }

    @Test
    fun tileLabel_blankOrMissingTextNeverMatches() {
        val labels = SystemComponents.mergeLabels(listOf("", "  ", null))
        assertThat(SystemComponents.startsWithLabel(null, labels)).isFalse()
        assertThat(SystemComponents.startsWithLabel("   ", labels)).isFalse()
        assertThat(SystemComponents.startsWithLabel("Anything", listOf("", " "))).isFalse()
    }

    @Test
    fun localisedLabels_areKeptAlongsideTheEnglishFallback() {
        val labels = SystemComponents.mergeLabels(listOf("Internet", "Bluetooth", null, "Flugmodus", " WI-FI "))
        assertThat(labels).contains("Flugmodus")
        assertThat(labels).contains("Airplane mode")
        // Duplicates differing only in case or whitespace collapse to one entry.
        assertThat(labels.count { it.equals("Wi-Fi", ignoreCase = true) }).isEqualTo(1)
        assertThat(labels.count { it.equals("Internet", ignoreCase = true) }).isEqualTo(1)
    }

    @Test
    fun panelViewIds_areFullyQualifiedToSystemUi() {
        assertThat(SystemComponents.QS_PANEL_FULL_VIEW_IDS).contains("com.android.systemui:id/quick_qs_panel")
        assertThat(SystemComponents.QS_PANEL_FULL_VIEW_IDS.all { it.startsWith("com.android.systemui:id/") }).isTrue()
    }

    @Test
    fun hostedPicker_isRecognisedOnlyShortlyAfterAppLockOpenedOne() {
        assertThat(HostedPickerTracker.isHosted(1_000L)).isFalse()
        HostedPickerTracker.markLaunched(1_000L)
        assertThat(HostedPickerTracker.isHosted(1_000L)).isTrue()
        assertThat(HostedPickerTracker.isHosted(1_000L + HostedPickerTracker.WINDOW_MS)).isTrue()
        assertThat(HostedPickerTracker.isHosted(1_001L + HostedPickerTracker.WINDOW_MS)).isFalse()
    }

    @Test
    fun hostedPicker_clockGoingBackwardsIsNotAMatch() {
        HostedPickerTracker.markLaunched(50_000L)
        assertThat(HostedPickerTracker.isHosted(10_000L)).isFalse()
    }

    private fun guarded(
        packageName: String,
        pickers: Boolean = false,
        quickSettings: Boolean = false,
        accessibility: Boolean = true,
    ) = SystemComponents.isGuarded(packageName, pickers, quickSettings, accessibility)
}
