package dev.riyadm.applock.data.repository

import android.content.Context
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class SystemComponentSettingsTest {

    private lateinit var repo: PreferencesRepository

    @Before
    fun setUp() {
        val context: Context = RuntimeEnvironment.getApplication()
        repo = PreferencesRepository(context)
    }

    @Test
    fun bothLocks_areOffByDefault() {
        assertThat(repo.isLockSystemPickersEnabled()).isFalse()
        assertThat(repo.isLockQuickSettingsEnabled()).isFalse()
    }

    @Test
    fun locks_persistIndependently() {
        repo.setLockSystemPickersEnabled(true)
        assertThat(repo.isLockSystemPickersEnabled()).isTrue()
        assertThat(repo.isLockQuickSettingsEnabled()).isFalse()

        repo.setLockQuickSettingsEnabled(true)
        repo.setLockSystemPickersEnabled(false)
        assertThat(repo.isLockSystemPickersEnabled()).isFalse()
        assertThat(repo.isLockQuickSettingsEnabled()).isTrue()
    }

    @Test
    fun locks_areExportedAndImportedLikeOtherBooleanSettings() {
        repo.setLockSystemPickersEnabled(true)
        repo.setLockQuickSettingsEnabled(true)

        val exported = repo.exportSettings()
        assertThat(exported["lock_system_pickers"]).isEqualTo(true)
        assertThat(exported["lock_quick_settings"]).isEqualTo(true)

        repo.setLockSystemPickersEnabled(false)
        repo.setLockQuickSettingsEnabled(false)
        val applied = repo.importSettings(exported)

        assertThat(applied).isAtLeast(2)
        assertThat(repo.isLockSystemPickersEnabled()).isTrue()
        assertThat(repo.isLockQuickSettingsEnabled()).isTrue()
    }

    @Test
    fun wronglyTypedImport_isIgnored() {
        val applied = repo.importSettings(
            mapOf("lock_system_pickers" to "yes", "lock_quick_settings" to 1)
        )

        assertThat(applied).isEqualTo(0)
        assertThat(repo.isLockSystemPickersEnabled()).isFalse()
        assertThat(repo.isLockQuickSettingsEnabled()).isFalse()
    }
}
