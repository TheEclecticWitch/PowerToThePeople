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
        versionName = "0.1"
    }

    buildTypes {
        release {
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
