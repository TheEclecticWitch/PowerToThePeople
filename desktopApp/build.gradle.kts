import org.jetbrains.compose.desktop.application.dsl.TargetFormat

/**
 * The desktop host for the shared app - a window and nothing else. One module serves Windows,
 * macOS and Linux: Compose Desktop resolves the right native pieces for whichever machine builds it.
 */
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":shared"))
    // The plugin's accessor, not the catalog's desktop-jvm: only this one brings the Skia native
    // library for the machine doing the build. Without it the packaged app dies at start with
    // "Failed to launch JVM" (skiko-windows-x64.dll missing).
    implementation(compose.desktop.currentOs)
}

compose.desktop {
    application {
        mainClass = "com.theeclecticwitch.powertothepeople.MainKt"

        // Package with the JDK 21 toolchain Gradle provisions, not whatever runs Gradle: Android
        // Studio's bundled runtime has no jpackage, so a packaged app can't be made with it.
        javaHome = javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) }
            .get().metadata.installationPath.asFile.absolutePath

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Dmg, TargetFormat.Deb)
            packageName = "Power to the People"
            packageVersion = "1.0.0"

            /**
             * A packaged app carries its own trimmed Java runtime, which leaves out anything
             * nothing appears to reference. Found on Book of Shadows: these are reached
             * reflectively, so without them the packaged app dies where `:run` works fine.
             * Ktor's Java engine needs java.net.http, and HTTPS needs jdk.crypto.ec.
             */
            modules("java.instrument", "jdk.unsupported", "java.net.http", "jdk.crypto.ec", "java.naming")
        }
    }
}
