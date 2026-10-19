import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    id("dev.rikka.tools.refine") version "4.4.0"
}

// Release signing comes from environment variables (CI) or an untracked keystore.properties
// file (local). Without either, release builds fall back to the debug key.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun signingValue(env: String, property: String): String? =
    System.getenv(env)?.takeIf { it.isNotBlank() } ?: keystoreProperties.getProperty(property)

val releaseStoreFile = signingValue("SIGNING_KEYSTORE_PATH", "storeFile")
val hasReleaseSigning = releaseStoreFile != null && file(releaseStoreFile).exists()

android {
    namespace = "dev.riyadm.applock"
    compileSdk {
        version = release(37) {
            minorApiLevel = 2
        }
    }

    defaultConfig {
        applicationId = "dev.riyadm.applock"
        minSdk = 26
        targetSdk = 37
        versionCode = 310
        versionName = "3.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseStoreFile!!)
                storePassword = signingValue("SIGNING_KEYSTORE_PASSWORD", "storePassword")
                keyAlias = signingValue("SIGNING_KEY_ALIAS", "keyAlias")
                keyPassword = signingValue("SIGNING_KEY_PASSWORD", "keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true

            vcsInfo.include = false

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName(if (hasReleaseSigning) "release" else "debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlin.compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
    buildFeatures {
        compose = true
        aidl = true
    }

    androidResources {
        // Lets users pick the app language in system settings (Android 13+)
        generateLocaleConfig = true
    }

    lint {
        // New strings fall back to English until they are translated.
        warning += setOf("MissingTranslation", "LocalContextGetResourceValueCall")
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        // Robolectric downloads its Android jars through java.io.tmpdir; keep that off a small /tmp
        unitTests.all { test ->
            val tmp = layout.buildDirectory.dir("tmp/unitTest").get().asFile
            test.doFirst { tmp.mkdirs() }
            test.systemProperty("java.io.tmpdir", tmp.absolutePath)
            // Robolectric's Android 16 runtime reaches into FileDescriptor internals
            test.jvmArgs(
                "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",
                "--enable-native-access=ALL-UNNAMED"
            )
        }
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

dependencies {
    implementation(project(":appintro"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.material.icons.core)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.profileinstaller)

    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
    implementation(libs.refine.runtime)
    compileOnly(project(":hidden-api"))
    implementation(libs.hiddenapibypass)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.truth)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}

// The Refine plugin instruments every dependency of every component, unit tests included.
// That rewrites the signed BouncyCastle jar Robolectric loads, so its classes no longer match
// the jar signature. Swap in the original jar on the unit test classpath.
val unitTestSignedJars: Configuration by configurations.creating {
    isCanBeConsumed = false
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
        attribute(
            TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE,
            objects.named(TargetJvmEnvironment.STANDARD_JVM)
        )
    }
}

dependencies {
    unitTestSignedJars(libs.robolectric)
}

tasks.withType<Test>().configureEach {
    val signedJars = unitTestSignedJars.filter { it.name.startsWith("bcprov") }
    doFirst {
        classpath = signedJars + classpath.filter { !it.name.startsWith("bcprov") }
    }
}
