import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-test-fixtures`
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
        freeCompilerArgs.addAll(
            // Ports keep Dart's names, including leading underscores and
            // lowerCamel enum constants, so they can be diffed side by side.
            "-Xsuppress-warning=NOTHING_TO_INLINE",
        )
    }
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    api(libs.okhttp)

    // Shared test helpers (GangTestRule, MockClient), also used by :app tests.
    testFixturesApi(libs.junit)
    testFixturesApi(libs.kotlinx.coroutines.test)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}

tasks.test {
    // Local time arithmetic (calendar days, DST) must match the Dart tests,
    // which ran in UTC on CI.
    systemProperty("user.timezone", "UTC")
    jvmArgs("-Duser.timezone=UTC")
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
