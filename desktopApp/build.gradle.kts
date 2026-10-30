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

/**
 * What the Mac App Store copy carries inside it for Tip the Creator (2026-10-24, as in Consciousness Theories):
 *
 * - JNA's native part. In the sandbox, anything the app unpacks for itself is quarantined and macOS refuses to load
 *   it ("Apple could not verify ... is free of malware", seen in the Book of Shadows); shipped in the app, it is signed
 *   with the rest, and Main points JNA at it.
 * - The StoreKit library (macos/TipStoreKit.swift - StoreKit 2 is Swift-only), built with the Mac's own Swift compiler,
 *   so only on a Mac; elsewhere there is nothing to build and the tip card hides on a Mac. Apple silicon only.
 *
 * The `macos-<arch>` folders are Compose's: each Mac gets only its own chip's; Windows gets neither (it opens Ko-fi).
 */
val nativesForMac = tasks.register<Copy>("prepareNativesForMac") {
    val jars = configurations.getByName("runtimeClasspath")
    into(layout.buildDirectory.dir("appResources"))
    listOf("darwin-aarch64" to "macos-arm64", "darwin-x86-64" to "macos-x64").forEach { (jnaName, composeName) ->
        from({ jars.filter { it.name.startsWith("jna-5") }.map { zipTree(it) } }) {
            include("com/sun/jna/$jnaName/libjnidispatch.jnilib")
            eachFile { path = "$composeName/libjnidispatch.dylib" }
            includeEmptyDirs = false
        }
    }
}
val macStoreKit = tasks.register<Exec>("buildMacStoreKit") {
    val source = project.file("macos/TipStoreKit.swift")
    val out = layout.buildDirectory.file("appResources/macos-arm64/libp2pstorekit.dylib")
    inputs.file(source)
    outputs.file(out)
    onlyIf { System.getProperty("os.name").startsWith("Mac") }
    doFirst { out.get().asFile.parentFile.mkdirs() }
    commandLine(
        "xcrun", "swiftc", "-emit-library", "-O", "-swift-version", "5", "-module-name", "P2PStoreKit",
        "-target", "arm64-apple-macos12", "-Xlinker", "-install_name", "-Xlinker", "@rpath/libp2pstorekit.dylib",
        source.absolutePath, "-o", out.get().asFile.absolutePath,
    )
}
nativesForMac.configure { finalizedBy(macStoreKit) }

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
            packageVersion = "1.1.2"
            // A folder of per-platform folders (macos-arm64, ...), filled by prepareNativesForMac: Tip the Creator's
            // StoreKit library for the Mac. Windows carries nothing extra (it tips through Ko-fi).
            appResourcesRootDir.set(layout.dir(nativesForMac.map { it.destinationDir }))
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
