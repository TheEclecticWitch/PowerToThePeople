import java.util.Properties

/** The Android host for the shared app: an Activity and nothing else. */
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.theeclecticwitch.powertothepeople"
    // Compose Multiplatform 1.12 pulls androidx.compose 1.12, which needs 37.
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.theeclecticwitch.powertothepeople"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }

    // The Google Play upload key lives outside the project (never in git): ~/.ptp-signing/keystore.properties.
    // Without it, release builds come out unsigned, and Android Studio's "Generate Signed App Bundle" still works.
    val uploadKey = File(System.getProperty("user.home"), ".ptp-signing/keystore.properties")
        .takeIf { it.exists() }
        ?.let { f -> Properties().apply { f.inputStream().use { load(it) } } }
    signingConfigs {
        if (uploadKey != null) {
            create("upload") {
                storeFile = file(uploadKey.getProperty("storeFile"))
                storePassword = uploadKey.getProperty("storePassword")
                keyAlias = uploadKey.getProperty("keyAlias")
                keyPassword = uploadKey.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            if (uploadKey != null) signingConfig = signingConfigs.getByName("upload")
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.mp.ui)
}
