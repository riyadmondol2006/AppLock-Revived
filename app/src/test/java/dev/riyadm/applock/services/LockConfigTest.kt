package dev.riyadm.applock.services

import android.content.Context
import com.google.common.truth.Truth.assertThat
import dev.riyadm.applock.data.repository.AppLockRepository
import dev.riyadm.applock.data.repository.BackendImplementation
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
class LockConfigTest {

    private lateinit var repo: AppLockRepository
    private lateinit var config: LockConfig

    @Before
    fun setUp() {
        val context: Context = RuntimeEnvironment.getApplication()
        repo = AppLockRepository(context)
        config = LockConfig(context, repo)
    }

    @Test
    fun followsTheRepository() {
        assertThat(config.protectEnabled).isEqualTo(repo.isProtectEnabled())
        assertThat(config.backend).isEqualTo(repo.getBackendImplementation())
        assertThat(config.hasLockedApps()).isFalse()

        repo.setProtectEnabled(false)
        repo.setBackendImplementation(BackendImplementation.SHIZUKU)
        repo.setAntiUninstallEnabled(true)
        ShadowLooper.idleMainLooper()

        assertThat(config.protectEnabled).isFalse()
        assertThat(config.backend).isEqualTo(BackendImplementation.SHIZUKU)
        assertThat(config.antiUninstallEnabled).isTrue()
    }

    @Test
    fun lockedAppsAreAlwaysCurrent() {
        repo.addLockedApp("com.example.chat")
        // No listener round trip needed: the set is read through.
        assertThat(config.isLocked("com.example.chat")).isTrue()
        assertThat(config.hasLockedApps()).isTrue()

        repo.removeLockedApp("com.example.chat")
        assertThat(config.isLocked("com.example.chat")).isFalse()
        assertThat(config.hasLockedApps()).isFalse()
    }

    @Test
    fun profileSwitchChangesTheLockedApps() {
        repo.addLockedApp("com.example.home")
        val other = repo.profiles.snapshot().profiles.first { it.id != repo.getActiveProfile().id }
        repo.switchProfile(other.id)

        assertThat(config.lockedApps()).isEqualTo(other.lockedApps)
    }

    @Test
    fun notifiesListenersOnChange() {
        var calls = 0
        val listener: () -> Unit = { calls++ }
        config.addListener(listener)

        repo.setProtectEnabled(false)
        repo.addLockedApp("com.example.bank")
        ShadowLooper.idleMainLooper()
        assertThat(calls).isAtLeast(2)

        config.removeListener(listener)
        val before = calls
        repo.setProtectEnabled(true)
        ShadowLooper.idleMainLooper()
        assertThat(calls).isEqualTo(before)
    }
}
