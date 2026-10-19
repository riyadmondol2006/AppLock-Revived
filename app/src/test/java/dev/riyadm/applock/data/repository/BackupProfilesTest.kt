package dev.riyadm.applock.data.repository

import android.content.Context
import androidx.core.content.edit
import com.google.common.truth.Truth.assertThat
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class BackupProfilesTest {

    private lateinit var context: Context
    private lateinit var repo: AppLockRepository
    private lateinit var backup: BackupRepository

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        repo = AppLockRepository(context)
        backup = BackupRepository(context, repo)
    }

    /** Home: h1, h2 · Work: w1 (5 min, active) · custom "Reading": h1, h2 · levels on h1 and w1. */
    private fun populate(): String {
        repo.addLockedApp("com.home.one")
        repo.addLockedApp("com.home.two")
        val custom = repo.profiles.create("Reading")
        repo.setProfileMembership(LockProfilesRepository.ID_WORK, "com.work.one", true)
        repo.profiles.setUnlockDuration(LockProfilesRepository.ID_WORK, 5)
        repo.appPolicies.setLevel("com.home.one", SecurityLevel.TWO_STEP)
        repo.appPolicies.setLevel("com.work.one", SecurityLevel.BIOMETRIC_ONLY)
        assertThat(repo.switchProfile(LockProfilesRepository.ID_WORK)).isTrue()
        return custom.id
    }

    private fun profile(id: String): LockProfile =
        repo.profiles.snapshot().profiles.first { it.id == id }

    @Test
    fun plainRoundTrip_restoresProfilesAndSecurityLevels() {
        val customId = populate()
        val profilesBefore = repo.profiles.snapshot()
        val levelsBefore = repo.appPolicies.levels.value
        val text = backup.exportToString()

        // Change everything the backup covers.
        repo.switchProfile(LockProfilesRepository.ID_HOME)
        repo.profiles.delete(customId)
        repo.profiles.setUnlockDuration(LockProfilesRepository.ID_WORK, null)
        repo.profiles.rename(LockProfilesRepository.ID_HOME, "Renamed")
        repo.removeLockedApp("com.home.two")
        repo.appPolicies.setLevel("com.home.one", SecurityLevel.DEFAULT)
        repo.appPolicies.setLevel("com.other", SecurityLevel.CREDENTIAL_ONLY)

        val result = backup.importFromString(text)

        assertThat(repo.profiles.snapshot()).isEqualTo(profilesBefore)
        assertThat(repo.profiles.state.value).isEqualTo(profilesBefore)
        assertThat(repo.getActiveProfile().id).isEqualTo(LockProfilesRepository.ID_WORK)
        assertThat(repo.getLockedApps()).containsExactly("com.work.one")
        assertThat(repo.getUnlockTimeDuration()).isEqualTo(5)
        assertThat(profile(customId).customName).isEqualTo("Reading")
        assertThat(profile(customId).lockedApps).containsExactly("com.home.one", "com.home.two")
        assertThat(repo.appPolicies.levels.value).isEqualTo(levelsBefore)
        assertThat(repo.policyFor("com.home.one").requireBoth).isTrue()
        assertThat(result.profiles).isEqualTo(4)
        assertThat(result.securityLevels).isEqualTo(2)
    }

    @Test
    fun encryptedRoundTrip_restoresProfilesAndSecurityLevels() {
        populate()
        val profilesBefore = repo.profiles.snapshot()
        val levelsBefore = repo.appPolicies.levels.value
        val password = "correct horse".toCharArray()
        val text = backup.exportToString(password.copyOf())
        assertThat(text).doesNotContain("com.work.one")

        repo.switchProfile(LockProfilesRepository.ID_HOME)
        repo.appPolicies.replaceAll(emptyMap())

        backup.importFromString(text, password)

        assertThat(repo.profiles.snapshot()).isEqualTo(profilesBefore)
        assertThat(repo.appPolicies.levels.value).isEqualTo(levelsBefore)
    }

    @Test
    fun export_containsProfilesAndLevels_butNoTrustedAutomationOrIntruderSettings() {
        populate()
        context.getSharedPreferences("app_lock_settings", Context.MODE_PRIVATE).edit(commit = true) {
            putBoolean("trusted_wifi_enabled", true)
            putString("trusted_wifi_networks", "[]")
            putBoolean("automation_enabled", true)
            putBoolean("intruder_selfie_enabled", true)
        }

        val root = JSONObject(backup.exportToString())

        assertThat(root.getJSONObject("profiles").getString("active")).isEqualTo(LockProfilesRepository.ID_WORK)
        assertThat(root.getJSONObject("profiles").getJSONArray("items").length()).isEqualTo(4)
        assertThat(root.getJSONObject("appSecurityLevels").getString("com.home.one")).isEqualTo("TWO_STEP")
        val text = root.toString()
        assertThat(text).doesNotContain("trusted_wifi")
        assertThat(text).doesNotContain("automation_")
        assertThat(text).doesNotContain("intruder_")
    }

    private fun oldBackup(locked: List<String>): JSONObject = JSONObject()
        .put("format", "dev.riyadm.applock.backup")
        .put("version", 1)
        .put("lockedApps", JSONArray(locked))
        .put("triggerExcludedApps", JSONArray())
        .put("antiUninstallApps", JSONArray())
        .put("settings", JSONObject().put("unlock_time_duration", 15))

    private fun assertOldBackupApplied(customId: String, result: BackupRepository.ImportResult) {
        // Active profile (Work) gets the backup's apps, the others are left alone.
        assertThat(repo.getActiveProfile().id).isEqualTo(LockProfilesRepository.ID_WORK)
        assertThat(repo.getLockedApps()).containsExactly("com.old.one", "com.old.two")
        assertThat(profile(LockProfilesRepository.ID_WORK).lockedApps)
            .containsExactly("com.old.one", "com.old.two")
        assertThat(profile(LockProfilesRepository.ID_WORK).unlockDurationMinutes).isEqualTo(5)
        assertThat(profile(LockProfilesRepository.ID_HOME).lockedApps)
            .containsExactly("com.home.one", "com.home.two")
        assertThat(profile(customId).customName).isEqualTo("Reading")
        assertThat(repo.appPolicies.levels.value).containsExactly(
            "com.home.one", SecurityLevel.TWO_STEP,
            "com.work.one", SecurityLevel.BIOMETRIC_ONLY
        )
        assertThat(repo.getGlobalUnlockTimeDuration()).isEqualTo(15)
        assertThat(result.lockedApps).isEqualTo(2)
        assertThat(result.profiles).isEqualTo(0)
        assertThat(result.securityLevels).isEqualTo(0)
    }

    @Test
    fun oldPlainBackup_goesIntoActiveProfile_andKeepsTheRest() {
        val customId = populate()
        val result = backup.importFromString(oldBackup(listOf("com.old.one", "com.old.two")).toString())
        assertOldBackupApplied(customId, result)
    }

    @Test
    fun oldEncryptedBackup_goesIntoActiveProfile_andKeepsTheRest() {
        val customId = populate()
        val password = "backup-pass".toCharArray()
        val plain = oldBackup(listOf("com.old.one", "com.old.two")).toString().toByteArray(Charsets.UTF_8)
        val sealed = BackupCrypto.encrypt(plain, password.copyOf(), iterations = 10_000)
        val envelope = JSONObject()
            .put("format", "dev.riyadm.applock.backup")
            .put("version", 2)
            .put("kdf", BackupCrypto.KDF)
            .put("iterations", sealed.iterations)
            .put("cipher", BackupCrypto.CIPHER)
            .put("salt", BackupCrypto.encode(sealed.salt))
            .put("iv", BackupCrypto.encode(sealed.iv))
            .put("ciphertext", BackupCrypto.encode(sealed.ciphertext))

        val result = backup.importFromString(envelope.toString(), password)
        assertOldBackupApplied(customId, result)
    }

    @Test
    fun profilesWithoutValidEntries_andMalformedLevels_areTreatedAsMissing() {
        val customId = populate()
        val root = oldBackup(listOf("com.old.one", "com.old.two"))
            .put(
                "profiles", JSONObject()
                    .put("active", "home")
                    .put(
                        "items", JSONArray()
                            .put(JSONObject().put("id", "home").put("kind", "home"))
                            .put(JSONObject().put("id", "").put("kind", "WORK"))
                            .put("not a profile")
                    )
            )
            .put("appSecurityLevels", JSONArray().put("TWO_STEP"))

        val result = backup.importFromString(root.toString())
        assertOldBackupApplied(customId, result)
    }

    @Test
    fun invalidEntries_areSkipped() {
        populate()
        val items = JSONArray()
            // Valid, but with a bad name, bad app entries and a string duration.
            .put(
                JSONObject().put("id", "home").put("kind", "HOME").put("name", 7)
                    .put("apps", JSONArray().put("ignored.for.active").put(5).put(""))
                    .put("unlockMinutes", "5")
            )
            .put(JSONObject().put("id", "").put("kind", "WORK"))
            .put(JSONObject().put("id", "x".repeat(65)).put("kind", "WORK"))
            .put(JSONObject().put("kind", "WORK"))
            .put(JSONObject().put("id", "bogus").put("kind", "BOGUS"))
            .put(JSONObject().put("id", "lower").put("kind", "custom"))
            .put(JSONObject().put("id", "badapps").put("kind", "CUSTOM").put("apps", "com.a"))
            .put("not an object")
            .put(JSONObject().put("id", "home").put("kind", "GUEST"))
            .put(JSONObject().put("id", "c1").put("kind", "CUSTOM").put("name", "  Mine  ").put("unlockMinutes", -3))
            .put(JSONObject().put("id", "c2").put("kind", "CUSTOM").put("name", "   ").put("unlockMinutes", 2.5))
            .put(
                JSONObject().put("id", "c3").put("kind", "CUSTOM").put("name", "n".repeat(50))
                    .put("unlockMinutes", 15)
                    .put("apps", JSONArray().put("com.p").put(JSONObject()).put(" ").put("com.p"))
            )
        val root = oldBackup(listOf("com.active"))
            .put("profiles", JSONObject().put("active", "missing").put("items", items))
            .put(
                "appSecurityLevels", JSONObject()
                    .put("com.a", "TWO_STEP")
                    .put("com.b", "two_step")
                    .put("com.c", 5)
                    .put("com.d", "DEFAULT")
                    .put(" ", "TWO_STEP")
                    .put("com.e", "CREDENTIAL_ONLY")
                    .put("com.f", JSONObject.NULL)
            )

        val result = backup.importFromString(root.toString())

        val state = repo.profiles.snapshot()
        assertThat(state.profiles.map { it.id }).containsExactly("home", "c1", "c2", "c3").inOrder()
        // Unknown active id falls back to the first profile, which gets the backup's locked apps.
        assertThat(state.activeId).isEqualTo("home")
        assertThat(repo.getLockedApps()).containsExactly("com.active")
        val home = state.profiles[0]
        assertThat(home.kind).isEqualTo(ProfileKind.HOME)
        assertThat(home.customName).isNull()
        assertThat(home.lockedApps).containsExactly("com.active")
        assertThat(home.unlockDurationMinutes).isNull()
        assertThat(state.profiles[1].customName).isEqualTo("Mine")
        assertThat(state.profiles[1].unlockDurationMinutes).isNull()
        assertThat(state.profiles[2].customName).isNull()
        assertThat(state.profiles[2].unlockDurationMinutes).isNull()
        assertThat(state.profiles[3].customName).hasLength(LockProfilesRepository.MAX_NAME_LENGTH)
        assertThat(state.profiles[3].unlockDurationMinutes).isEqualTo(15)
        assertThat(state.profiles[3].lockedApps).containsExactly("com.p")
        assertThat(repo.getUnlockTimeDuration()).isEqualTo(15) // home has no override: global from settings

        assertThat(repo.appPolicies.levels.value).containsExactly(
            "com.a", SecurityLevel.TWO_STEP,
            "com.e", SecurityLevel.CREDENTIAL_ONLY
        )
        assertThat(result.profiles).isEqualTo(4)
        assertThat(result.securityLevels).isEqualTo(2)
    }

    @Test
    fun trustedAndAutomationSettingsInABackup_areNotImported() {
        val root = oldBackup(emptyList()).put(
            "settings", JSONObject()
                .put("trusted_wifi_enabled", true)
                .put("automation_enabled", true)
                .put("intruder_selfie_enabled", true)
        )
        val result = backup.importFromString(root.toString())

        val prefs = context.getSharedPreferences("app_lock_settings", Context.MODE_PRIVATE)
        assertThat(prefs.contains("trusted_wifi_enabled")).isFalse()
        assertThat(prefs.contains("automation_enabled")).isFalse()
        assertThat(prefs.contains("intruder_selfie_enabled")).isFalse()
        assertThat(result.settings).isEqualTo(0)
    }
}
