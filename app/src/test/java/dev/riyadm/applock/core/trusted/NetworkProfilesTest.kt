package dev.riyadm.applock.core.trusted

import android.net.wifi.WifiInfo
import android.os.Build
import com.google.common.truth.Truth.assertThat
import dev.riyadm.applock.core.trusted.TrustedNetworkMonitor.CurrentWifi
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NetworkProfilesTest {

    private val sdk = Build.VERSION_CODES.S
    private val profiles = setOf("home", "work", "guest")

    private fun wifi(
        ssid: String = "\"Home\"",
        bssid: String? = "aa:bb:cc:dd:ee:01",
        security: Int = WifiInfo.SECURITY_TYPE_PSK,
    ) = CurrentWifi(ssid, bssid, security)

    private fun rule(profileId: String, ssid: String = "\"Home\"", bssid: String? = null) =
        NetworkProfileRule(TrustedNetwork(ssid, bssid, WifiInfo.SECURITY_TYPE_PSK, 0L), profileId)

    private fun decide(
        situation: NetworkSituation,
        rules: List<NetworkProfileRule> = emptyList(),
        default: String? = null,
        existing: Set<String> = profiles,
    ) = NetworkProfiles.decide(situation, rules, default, existing, sdk)

    @Test
    fun mappedNetworkActivatesItsProfile() {
        val decision = decide(NetworkSituation.Wifi(listOf(wifi())), listOf(rule("work")), default = "guest")
        assertThat(decision.targetProfileId).isEqualTo("work")
        assertThat(decision.situationKey).isNotNull()
    }

    @Test
    fun otherNetworkUsesTheDefaultProfile() {
        val decision = decide(
            NetworkSituation.Wifi(listOf(wifi(ssid = "\"Cafe\""))),
            listOf(rule("work")),
            default = "guest"
        )
        assertThat(decision.targetProfileId).isEqualTo("guest")
    }

    @Test
    fun noDefaultLeavesUnmappedNetworksAlone() {
        val decision = decide(NetworkSituation.Wifi(listOf(wifi(ssid = "\"Cafe\""))), listOf(rule("work")))
        assertThat(decision.targetProfileId).isNull()
        assertThat(decision.situationKey).isNotNull()
    }

    @Test
    fun mobileDataAndNoConnectionUseTheDefault() {
        assertThat(decide(NetworkSituation.Offline, default = "guest").targetProfileId).isEqualTo("guest")
        assertThat(decide(NetworkSituation.Offline).targetProfileId).isNull()
    }

    @Test
    fun unreadableNetworkChangesNothing() {
        val decision = decide(NetworkSituation.Unreadable, listOf(rule("work")), default = "guest")
        assertThat(decision.targetProfileId).isNull()
        assertThat(decision.situationKey).isNull()
        val noName = decide(NetworkSituation.Wifi(emptyList()), default = "guest")
        assertThat(noName.targetProfileId).isNull()
        assertThat(noName.situationKey).isNull()
    }

    @Test
    fun deletedProfileFallsBackToTheDefault() {
        val decision = decide(
            NetworkSituation.Wifi(listOf(wifi())),
            listOf(rule("custom_gone")),
            default = "guest"
        )
        assertThat(decision.targetProfileId).isEqualTo("guest")
    }

    @Test
    fun deletedDefaultProfileChangesNothing() {
        assertThat(decide(NetworkSituation.Offline, default = "custom_gone").targetProfileId).isNull()
    }

    @Test
    fun openAndWepNetworksNeverMatch() {
        val open = wifi(security = WifiInfo.SECURITY_TYPE_OPEN)
        val wep = wifi(security = WifiInfo.SECURITY_TYPE_WEP)
        for (current in listOf(open, wep)) {
            val decision = decide(NetworkSituation.Wifi(listOf(current)), listOf(rule("work")), default = "guest")
            assertThat(decision.targetProfileId).isEqualTo("guest")
        }
    }

    @Test
    fun ruleWithRouterOnlyMatchesThatRouter() {
        val rules = listOf(rule("work", bssid = "aa:bb:cc:dd:ee:01"))
        assertThat(decide(NetworkSituation.Wifi(listOf(wifi())), rules).targetProfileId).isEqualTo("work")
        val other = wifi(bssid = "11:22:33:44:55:66")
        assertThat(decide(NetworkSituation.Wifi(listOf(other)), rules).targetProfileId).isNull()
    }

    @Test
    fun connectionsThatDisagreeChangeNothing() {
        val rules = listOf(rule("work"), rule("guest", ssid = "\"Cafe\""))
        val both = NetworkSituation.Wifi(listOf(wifi(), wifi(ssid = "\"Cafe\"")))
        val decision = decide(both, rules, default = "home")
        assertThat(decision.targetProfileId).isNull()
        assertThat(decision.situationKey).isNotNull()
    }

    @Test
    fun situationKeyIdentifiesTheConnection() {
        val a = decide(NetworkSituation.Wifi(listOf(wifi()))).situationKey
        val sameAgain = decide(NetworkSituation.Wifi(listOf(wifi(bssid = "AA:BB:CC:DD:EE:01")))).situationKey
        val otherRouter = decide(NetworkSituation.Wifi(listOf(wifi(bssid = "aa:bb:cc:dd:ee:02")))).situationKey
        val offline = decide(NetworkSituation.Offline).situationKey
        assertThat(a).isEqualTo(sameAgain)
        assertThat(a).isNotEqualTo(otherRouter)
        assertThat(a).isNotEqualTo(offline)
    }

    @Test
    fun switchesOnlyWhenTheConnectionChangedAndTheProfileDiffers() {
        val decision = NetworkProfileDecision("wifi:a", "work")
        assertThat(NetworkProfiles.plan(decision, lastKey = null, activeProfileId = "home"))
            .isEqualTo(NetworkProfileAction.SWITCH)
        // Same connection as last time: a manual switch stays.
        assertThat(NetworkProfiles.plan(decision, lastKey = "wifi:a", activeProfileId = "home"))
            .isEqualTo(NetworkProfileAction.NONE)
        // Another connection re-applies.
        assertThat(NetworkProfiles.plan(decision, lastKey = "offline", activeProfileId = "home"))
            .isEqualTo(NetworkProfileAction.SWITCH)
    }

    @Test
    fun alreadyOnTheTargetOnlyRemembersTheConnection() {
        val decision = NetworkProfileDecision("wifi:a", "work")
        assertThat(NetworkProfiles.plan(decision, lastKey = null, activeProfileId = "work"))
            .isEqualTo(NetworkProfileAction.REMEMBER)
    }

    @Test
    fun noTargetRemembersButNeverSwitches() {
        assertThat(NetworkProfiles.plan(NetworkProfileDecision("offline", null), null, "home"))
            .isEqualTo(NetworkProfileAction.REMEMBER)
        assertThat(NetworkProfiles.plan(NetworkProfileDecision(null, null), null, "home"))
            .isEqualTo(NetworkProfileAction.NONE)
    }
}
