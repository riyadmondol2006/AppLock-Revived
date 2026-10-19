package dev.riyadm.applock.core.trusted

import android.net.wifi.WifiInfo
import android.os.Build
import com.google.common.truth.Truth.assertThat
import dev.riyadm.applock.core.trusted.TrustedNetworkMonitor.CurrentWifi
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WifiTrustTest {

    private val android11 = Build.VERSION_CODES.R
    private val android12 = Build.VERSION_CODES.S
    private val bssid = "aa:bb:cc:dd:ee:ff"

    private fun saved(
        bssid: String? = this.bssid,
        security: Int = WifiInfo.SECURITY_TYPE_PSK,
        ssid: String = "\"Home\""
    ) = TrustedNetwork(ssid = ssid, bssid = bssid, securityType = security, addedAt = 0L)

    private fun current(
        bssid: String? = this.bssid,
        security: Int = WifiInfo.SECURITY_TYPE_PSK,
        ssid: String = "\"Home\""
    ) = CurrentWifi(ssid = ssid, bssid = bssid, securityType = security)

    @Test
    fun sameRouterAndSecurityMatches() {
        assertThat(WifiTrust.matches(saved(), current(), android12)).isTrue()
        assertThat(WifiTrust.matches(saved(), current(), android11)).isTrue()
        assertThat(WifiTrust.matches(saved(), current(bssid = bssid.uppercase()), android12)).isTrue()
    }

    @Test
    fun differentNameOrRouterDoesNotMatch() {
        assertThat(WifiTrust.matches(saved(), current(ssid = "\"Home2\""), android12)).isFalse()
        assertThat(WifiTrust.matches(saved(), current(bssid = "11:22:33:44:55:66"), android12)).isFalse()
    }

    @Test
    fun routerAddressIsRequiredBeforeAndroid12() {
        assertThat(WifiTrust.matches(saved(bssid = null), current(), android11)).isFalse()
        assertThat(WifiTrust.matches(saved(bssid = null, security = TrustedNetwork.SECURITY_UNKNOWN), current(), android11))
            .isFalse()
        // From Android 12 the user may explicitly choose name-only matching.
        assertThat(WifiTrust.matches(saved(bssid = null), current(), android12)).isTrue()
    }

    @Test
    fun redactedRouterAddressIsNeverTrusted() {
        assertThat(WifiTrust.matches(saved(), current(bssid = null), android12)).isFalse()
        assertThat(WifiTrust.matches(saved(), current(bssid = "02:00:00:00:00:00"), android12)).isFalse()
        assertThat(WifiTrust.matches(saved(), current(bssid = "02:00:00:00:00:00"), android11)).isFalse()
        // A redacted address stored by an older version doesn't count as a pinned router.
        assertThat(WifiTrust.matches(saved(bssid = "02:00:00:00:00:00"), current(bssid = null), android11)).isFalse()
    }

    @Test
    fun openNetworksAreNeverTrusted() {
        val untrusted = listOf(
            WifiInfo.SECURITY_TYPE_OPEN,
            WifiInfo.SECURITY_TYPE_OWE,
            WifiInfo.SECURITY_TYPE_WEP,
            TrustedNetwork.SECURITY_UNKNOWN
        )
        for (type in untrusted) {
            for (sdk in listOf(android11, android12)) {
                assertThat(WifiTrust.matches(saved(), current(security = type), sdk)).isFalse()
                assertThat(WifiTrust.matches(saved(bssid = null), current(security = type), sdk)).isFalse()
                assertThat(
                    WifiTrust.matches(saved(security = TrustedNetwork.SECURITY_UNKNOWN), current(security = type), sdk)
                ).isFalse()
            }
            assertThat(current(security = type).canBeTrusted).isFalse()
        }
    }

    @Test
    fun securityMustNeedTheSameKindOfSecret() {
        assertThat(WifiTrust.matches(saved(), current(security = WifiInfo.SECURITY_TYPE_EAP), android12)).isFalse()
        // WPA2/WPA3 transition networks keep matching.
        assertThat(WifiTrust.matches(saved(), current(security = WifiInfo.SECURITY_TYPE_SAE), android12)).isTrue()
        // A network saved while its type was unknown still needs a secured connection now.
        assertThat(
            WifiTrust.matches(saved(security = TrustedNetwork.SECURITY_UNKNOWN), current(), android12)
        ).isTrue()
    }

    @Test
    fun savingNeedsARouterAddressWhenPinningOrBeforeAndroid12() {
        assertThat(WifiTrust.canSave(current(), matchRouter = true, sdkInt = android12)).isTrue()
        assertThat(WifiTrust.canSave(current(bssid = null), matchRouter = true, sdkInt = android12)).isFalse()
        assertThat(
            WifiTrust.canSave(current(bssid = "02:00:00:00:00:00"), matchRouter = true, sdkInt = android12)
        ).isFalse()
        assertThat(WifiTrust.canSave(current(bssid = null), matchRouter = false, sdkInt = android12)).isTrue()
        assertThat(WifiTrust.canSave(current(bssid = null), matchRouter = false, sdkInt = android11)).isFalse()
        assertThat(WifiTrust.canSave(current(), matchRouter = false, sdkInt = android11)).isTrue()
        assertThat(
            WifiTrust.canSave(current(security = WifiInfo.SECURITY_TYPE_OPEN), matchRouter = true, sdkInt = android12)
        ).isFalse()
    }

    @Test
    fun scanCapabilitiesMapToSecurityTypes() {
        fun type(caps: String?) = WifiTrust.securityTypeFromCapabilities(caps)
        assertThat(type("[WPA2-PSK-CCMP][RSN-PSK-CCMP][ESS]")).isEqualTo(WifiInfo.SECURITY_TYPE_PSK)
        assertThat(type("[WPA-PSK-TKIP][ESS][WPS]")).isEqualTo(WifiInfo.SECURITY_TYPE_PSK)
        assertThat(type("[RSN-SAE-CCMP][ESS]")).isEqualTo(WifiInfo.SECURITY_TYPE_SAE)
        assertThat(type("[RSN-PSK+SAE-CCMP][ESS]")).isEqualTo(WifiInfo.SECURITY_TYPE_SAE)
        assertThat(type("[WPA2-EAP-CCMP][ESS]")).isEqualTo(WifiInfo.SECURITY_TYPE_EAP)
        assertThat(type("[RSN-OWE-CCMP][ESS]")).isEqualTo(WifiInfo.SECURITY_TYPE_OWE)
        assertThat(type("[WEP][ESS]")).isEqualTo(WifiInfo.SECURITY_TYPE_WEP)
        assertThat(type("[ESS]")).isEqualTo(WifiInfo.SECURITY_TYPE_OPEN)
        assertThat(type("")).isEqualTo(TrustedNetwork.SECURITY_UNKNOWN)
        assertThat(type(null)).isEqualTo(TrustedNetwork.SECURITY_UNKNOWN)

        for (caps in listOf("[ESS]", "[RSN-OWE-CCMP][ESS]", "[WEP][ESS]", null)) {
            assertThat(WifiTrust.canBeTrusted(type(caps))).isFalse()
        }
        for (caps in listOf("[WPA2-PSK-CCMP][ESS]", "[RSN-SAE-CCMP][ESS]", "[WPA2-EAP-CCMP][ESS]")) {
            assertThat(WifiTrust.canBeTrusted(type(caps))).isTrue()
        }
    }
}
