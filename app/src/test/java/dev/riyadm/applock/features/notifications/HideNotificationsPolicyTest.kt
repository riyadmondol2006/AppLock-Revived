package dev.riyadm.applock.features.notifications

import android.app.Notification
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HideNotificationsPolicyTest {

    private val locked = setOf("com.chat", "com.bank")

    private fun hide(
        enabled: Boolean = true,
        postedBy: String = "com.chat",
        locked: Set<String> = this.locked,
        withinWindow: Boolean = false,
        exempt: Boolean = false,
    ) = HideNotificationsPolicy.shouldHide(enabled, "dev.riyadm.applock", postedBy, locked, withinWindow, exempt)

    @Test
    fun hidesNotificationsOfLockedApps() {
        assertThat(hide()).isTrue()
        assertThat(hide(postedBy = "com.bank")).isTrue()
    }

    @Test
    fun leavesUnlockedAppsAlone() {
        assertThat(hide(postedBy = "com.maps")).isFalse()
        assertThat(hide(locked = emptySet())).isFalse()
    }

    @Test
    fun doesNothingWhenDisabled() {
        assertThat(hide(enabled = false)).isFalse()
    }

    @Test
    fun neverTouchesItsOwnNotifications() {
        assertThat(hide(postedBy = "dev.riyadm.applock", locked = locked + "dev.riyadm.applock")).isFalse()
    }

    @Test
    fun respectsTheUnlockWindow() {
        assertThat(hide(withinWindow = true)).isFalse()
    }

    @Test
    fun exemptNotificationsAreKept() {
        assertThat(hide(exempt = true)).isFalse()
    }

    @Test
    fun exemptionCoversServicesOngoingAndCalls() {
        assertThat(HideNotificationsPolicy.isExempt(Notification.FLAG_FOREGROUND_SERVICE, null)).isTrue()
        assertThat(HideNotificationsPolicy.isExempt(Notification.FLAG_ONGOING_EVENT, null)).isTrue()
        assertThat(HideNotificationsPolicy.isExempt(0, Notification.CATEGORY_CALL)).isTrue()
        assertThat(HideNotificationsPolicy.isExempt(0, Notification.CATEGORY_TRANSPORT)).isTrue()
        assertThat(HideNotificationsPolicy.isExempt(0, Notification.CATEGORY_MESSAGE)).isFalse()
        assertThat(HideNotificationsPolicy.isExempt(Notification.FLAG_AUTO_CANCEL, null)).isFalse()
        assertThat(HideNotificationsPolicy.isExempt(Notification.FLAG_GROUP_SUMMARY, null)).isFalse()
    }

    @Test
    fun placeholderTagRoundTrips() {
        val tag = HideNotificationsPolicy.placeholderTag("com.chat")
        assertThat(HideNotificationsPolicy.packageOfPlaceholderTag(tag)).isEqualTo("com.chat")
        assertThat(HideNotificationsPolicy.packageOfPlaceholderTag("other")).isNull()
        assertThat(HideNotificationsPolicy.packageOfPlaceholderTag(null)).isNull()
    }

    @Test
    fun stalePlaceholders() {
        val shown = listOf("com.chat", "com.old")
        assertThat(HideNotificationsPolicy.stalePlaceholderPackages(shown, locked, enabled = true))
            .containsExactly("com.old")
        assertThat(HideNotificationsPolicy.stalePlaceholderPackages(shown, locked, enabled = false))
            .containsExactly("com.chat", "com.old")
    }

    @Test
    fun recentKeysDedupeByKeyAndPostTime() {
        val keys = RecentNotificationKeys(capacity = 2)
        assertThat(keys.markIfNew("a", 1)).isTrue()
        assertThat(keys.markIfNew("a", 1)).isFalse()
        assertThat(keys.markIfNew("a", 2)).isTrue() // an update of the same notification
        assertThat(keys.markIfNew("b", 1)).isTrue()
        assertThat(keys.markIfNew("c", 1)).isTrue() // evicts the oldest entry ("a")
        assertThat(keys.markIfNew("a", 2)).isTrue()
        assertThat(keys.markIfNew("c", 1)).isFalse()
    }
}
