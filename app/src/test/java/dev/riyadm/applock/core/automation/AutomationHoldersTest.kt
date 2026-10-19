package dev.riyadm.applock.core.automation

import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class AutomationHoldersTest {

    private val context: Context = RuntimeEnvironment.getApplication()

    private fun pkg(name: String, targetSdk: Int?, holdsPermission: Boolean = true) = PackageInfo().apply {
        packageName = name
        applicationInfo = targetSdk?.let { sdk ->
            ApplicationInfo().apply {
                packageName = name
                targetSdkVersion = sdk
            }
        }
        if (holdsPermission) {
            requestedPermissions = arrayOf(AutomationReceiver.PERMISSION)
            requestedPermissionsFlags = intArrayOf(PackageInfo.REQUESTED_PERMISSION_GRANTED)
        }
    }

    @Test
    fun modernHoldersDoNotBlock() {
        val holders = AutomationHolders.fromPackages(listOf(pkg("net.tasker", 34), pkg("com.macro", 23)), "own")
        assertThat(holders.map { it.packageName }).containsExactly("com.macro", "net.tasker").inOrder()
        assertThat(AutomationHolders.blocksCommands(holders)).isFalse()
        assertThat(AutomationHolders.blocksCommands(emptyList())).isFalse()
    }

    @Test
    fun legacyHolderBlocksEverything() {
        val holders = AutomationHolders.fromPackages(listOf(pkg("net.tasker", 34), pkg("old.app", 22)), "own")
        assertThat(holders.single { it.packageName == "old.app" }.legacy).isTrue()
        assertThat(AutomationHolders.blocksCommands(holders)).isTrue()
    }

    @Test
    fun unknownTargetIsTreatedAsLegacy() {
        val holders = AutomationHolders.fromPackages(listOf(pkg("mystery.app", null)), "own")
        assertThat(holders.single().legacy).isTrue()
        assertThat(AutomationHolders.blocksCommands(holders)).isTrue()
    }

    @Test
    fun failureToListHoldersBlocks() {
        assertThat(AutomationHolders.blocksCommands(null)).isTrue()
    }

    @Test
    fun ownPackageIsIgnored() {
        val holders = AutomationHolders.fromPackages(listOf(pkg("own", 22)), "own")
        assertThat(holders).isEmpty()
    }

    @Test
    fun packageManagerErrorFailsSecure() {
        val broken = object : ContextWrapper(context) {
            override fun getPackageManager(): PackageManager = throw IllegalStateException("binder died")
        }
        val holders = AutomationHolders.find(broken)
        assertThat(holders).isNull()
        assertThat(AutomationHolders.blocksCommands(holders)).isTrue()
    }
}
