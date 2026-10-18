import java.util.Properties
import org.jetbrains.kotlin.compose.compiler.gradle.ComposeFeatureFlag
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.roborazzi)
}

// Keep local/debug builds usable before a Firebase project is configured.
// Release CI injects this file from a secret when offline push is enabled.
if (file("google-services.json").exists()) {
    apply(plugin = libs.plugins.google.services.get().pluginId)
}

val releaseKeystorePath = System.getenv("ANDROID_KEYSTORE_PATH")
val releaseKeystorePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
val releaseKeyAlias = System.getenv("ANDROID_KEY_ALIAS")
val releaseKeyPassword = System.getenv("ANDROID_KEY_PASSWORD")
val releaseSigningValues =
    listOf(
        releaseKeystorePath,
        releaseKeystorePassword,
        releaseKeyAlias,
        releaseKeyPassword,
    )
val hasReleaseSigning = releaseSigningValues.all { !it.isNullOrBlank() }
require(releaseSigningValues.none { !it.isNullOrBlank() } || hasReleaseSigning) {
    "Android release signing requires all ANDROID_KEYSTORE_* and ANDROID_KEY_* environment variables."
}

// The version comes from android/version.properties (CI rewrites it from the
// release tag). versionCode = major*1000000 + minor*1000 + patch, the same
// scheme the Flutter build and the desktop clients use, so installed Flutter
// APKs upgrade to the native build in place.
val versionProperties =
    Properties().apply {
        rootProject.file("version.properties").inputStream().use { load(it) }
    }
val appVersionName: String = versionProperties.getProperty("versionName")
val appVersionCode: Int =
    appVersionName
        .substringBefore('+')
        .split('.')
        .map { it.toInt() }
        .let { (major, minor, patch) -> major * 1_000_000 + minor * 1_000 + patch }

android {
    namespace = "com.gangchat.client"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.gangchat.client"
        minSdk = 24
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // The ABIs the Flutter build shipped. JNA (through LiveKit) also
        // brings armeabi, mips and mips64 copies of libjnidispatch, and the x86
        // set lacks LiveKit's own library.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }

        // Flutter's --dart-define overrides (see AppConfig): set these
        // environment variables at build time to point a build elsewhere.
        for (name in listOf("GANG_API_BASE_URL", "GANG_ASSET_BASE_URL", "GANG_RELEASE_BUCKET_URL")) {
            buildConfigField("String", name, "\"${System.getenv(name).orEmpty()}\"")
        }
        // Settings > About shows this (Flutter's gangChatClientReleaseTimestamp).
        buildConfigField("String", "GANG_CHAT_RELEASE_TIMESTAMP", "\"${versionProperties.getProperty("releasedAt").orEmpty()}\"")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseKeystorePath!!)
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                // Android 7+ only needs v2, but some installers' safety checks
                // and third-party scanners only look for the v1 (JAR) files
                // and call a v2-only APK unsigned.
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        release {
            // Local release runs remain installable with the debug key. The
            // release workflow always supplies a stable private signing key so
            // published APKs can upgrade one another across versions.
            signingConfig =
                signingConfigs.getByName(if (hasReleaseSigning) "release" else "debug")
            isMinifyEnabled = false
        }
    }

    sourceSets {
        // Server config, branding and fonts live in the repository-level assets/
        // directory that the desktop client also reads.
        getByName("main").assets.directories.add("../../assets")
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.systemProperty("user.timezone", "UTC")
                it.maxHeapSize = "2g"
                // The opt-in end-to-end test (GangAppEndToEndTest) runs only
                // with these set; as inputs they also keep a run without them
                // from being reused from the build cache.
                for (name in listOf("GANG_E2E_API_BASE_URL", "GANG_E2E_ASSET_BASE_URL")) {
                    val value = System.getenv(name).orEmpty()
                    it.inputs.property(name, value)
                    if (value.isNotEmpty()) it.environment(name, value)
                }
            }
        }
    }

    packaging {
        resources.excludes += setOf("META-INF/{AL2.0,LGPL2.1}", "META-INF/versions/9/OSGI-INF/MANIFEST.MF")
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

composeCompiler {
    // Flutter rebuilds every child widget when a parent State calls setState.
    // The ported screens keep their state in plain fields of ViewState classes
    // (see docs/android-port-conventions.md), so children must recompose when
    // their parent does even if they receive the same controller instance.
    featureFlags.add(ComposeFeatureFlag.StrongSkipping.disabled())
}

dependencies {
    implementation(project(":core"))

    coreLibraryDesugaring(libs.desugar.jdk.libs)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.animation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    implementation(libs.activity.compose)
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.process)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.livekit.android)
    implementation(libs.protobuf.javalite)
    implementation(libs.coil.compose)
    implementation(libs.coil.gif)
    implementation(libs.media3.exoplayer)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)

    testImplementation(testFixtures(project(":core")))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.junit)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
}
