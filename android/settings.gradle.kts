pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // LiveKit's audio routing library (AudioSwitch fork) is only on JitPack.
        maven("https://jitpack.io") {
            content { includeGroup("com.github.davidliu") }
        }
    }
}

rootProject.name = "gang-chat-android"

// :core is plain Kotlin (no Android SDK): protocol, auth, app controllers and
// the foundation helpers that stand in for Dart's async/convert libraries.
// :app is the Android application: platform services, LiveKit and Compose UI.
include(":core")
include(":app")
