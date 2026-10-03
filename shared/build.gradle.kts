import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/**
 * Everything the app is: the data it fetches, what it keeps on the device, and the whole Compose UI.
 *
 * Since AGP 9 an Android application module cannot also be a multiplatform module, so the shared
 * code is a library and each platform brings a thin host - `:androidApp`, `:desktopApp` (Windows,
 * macOS and Linux) and `iosApp/`.
 */
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    android {
        namespace = "com.theeclecticwitch.powertothepeople.shared"
        compileSdk = 37
        minSdk = 26

        // Off by default in this plugin; without it the bundled Constitution and fonts never
        // reach the Android package, and reading them fails only at run time.
        androidResources {
            enable = true
        }

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    /**
     * Windows, macOS and Linux are one JVM target between them. Pinned to Java 21, the runtime the
     * packaged app carries: left alone it takes the Gradle daemon's Java 25, and the packaged app
     * then refuses to start ("compiled by a more recent version of the Java Runtime").
     */
    jvm("desktop") {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    /**
     * The device and the Apple Silicon simulator. No `iosX64`: Compose Multiplatform 1.12 no
     * longer publishes for the Intel simulator. Apple targets only compile on a Mac, so on
     * Windows these tasks are skipped - a green Windows build says nothing about iOS.
     */
    listOf(iosArm64(), iosSimulatorArm64()).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "PowerToThePeopleKit"
            isStatic = true
        }
    }

    compilerOptions {
        optIn.add("kotlin.time.ExperimentalTime")
        optIn.add("androidx.compose.material3.ExperimentalMaterial3Api")
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.mp.runtime)
            implementation(libs.compose.mp.foundation)
            implementation(libs.compose.mp.ui)
            implementation(libs.compose.mp.material3)
            implementation(libs.compose.mp.icons.core)
            implementation(libs.compose.mp.resources)
            implementation(libs.navigation.mp.compose)
            implementation(libs.lifecycle.mp.viewmodel.compose)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
            implementation(libs.coil3.compose)
            implementation(libs.coil3.network.ktor)
            implementation(libs.okio)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(libs.androidx.work.runtime)
            implementation(libs.androidx.activity.compose)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        val desktopMain by getting
        desktopMain.dependencies {
            implementation(libs.ktor.client.java)
            implementation(libs.compose.mp.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.ktor.client.mock)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.okio.fakefilesystem)
        }
    }
}

compose.resources {
    // Named explicitly so it never depends on the root project's name.
    packageOfResClass = "com.theeclecticwitch.powertothepeople.shared.resources"
    publicResClass = false
}
