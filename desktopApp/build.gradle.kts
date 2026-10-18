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
            targetFormats(TargetFormat.Msi, TargetFormat.Dmg, TargetFormat.Pkg, TargetFormat.Deb)
            packageName = "Power to the People"
            // The Microsoft Store wants a new number for every upload (tools/msix/build_msix.py reads it here).
            packageVersion = "1.0.0"
            vendor = "The Eclectic Witch"
            copyright = "© 2026 The Eclectic Witch"

            /**
             * A packaged app carries its own trimmed Java runtime, which leaves out anything
             * nothing appears to reference. Found on Book of Shadows: these are reached
             * reflectively, so without them the packaged app dies where `:run` works fine.
             * Ktor's Java engine needs java.net.http, and HTTPS needs jdk.crypto.ec.
             */
            modules("java.instrument", "jdk.unsupported", "java.net.http", "jdk.crypto.ec", "java.naming")

            // Drawn by tools/icon/desktop_icons.py from the same icon as the phones.
            windows {
                iconFile.set(project.file("icons/icon.ico"))
                menuGroup = "Power to the People"
                shortcut = true
            }
            macOS {
                iconFile.set(project.file("icons/icon.icns"))
                // Same bundle ID as the iPhone app: one App Store record ("P2P: Power to the People").
                bundleID = "com.theeclecticwitch.powertothepeople"
                appCategory = "public.app-category.reference"
                minimumSystemVersion = "12.0"
                // The Mac App Store build, made on the Mac by tools/mac-appstore-build.sh
                // (`-PmacAppStore -PmacBuild=<n>`, `packagePkg`), as for Consciousness Theories.
                // Needs the certificates "3rd Party Mac Developer Application/Installer: Rod Cameron
                // (9JTBG6NRXZ)" in the Mac's keychain and the two Mac App Store profiles in macos/
                // (made by tools/store/apple_setup.py; kept out of git, since this repo is public).
                if (providers.gradleProperty("macAppStore").isPresent) {
                    appStore = true
                    // The same number as the iPhone app's version on App Store Connect.
                    packageVersion = "1.0"
                    // Raised for every upload to App Store Connect.
                    packageBuildVersion = providers.gradleProperty("macBuild").getOrElse("1")
                    signing {
                        sign.set(true)
                        identity.set("Rod Cameron (9JTBG6NRXZ)")
                    }
                    provisioningProfile.set(project.file("macos/embedded.provisionprofile"))
                    runtimeProvisioningProfile.set(project.file("macos/runtime.provisionprofile"))
                    entitlementsFile.set(project.file("macos/entitlements.plist"))
                    runtimeEntitlementsFile.set(project.file("macos/runtime-entitlements.plist"))
                    infoPlist {
                        // Only the HTTPS that macOS itself provides: saves a question at every upload.
                        extraKeysRawXml = "  <key>ITSAppUsesNonExemptEncryption</key>\n  <false/>\n"
                    }
                }
            }
        }
    }
}
