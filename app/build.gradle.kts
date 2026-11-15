plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Single source of truth for the app version. The APK file name, builder.sh and the
// landing page all read these two values. For every release: bump both here and add a
// matching "## <version> (<code>) — <date>" entry at the top of CHANGELOG.md.
val appVersionName = "0.2.0"
val appVersionCode = 2

base {
    // Produces app/build/outputs/apk/debug/locked-v0.2.0-debug.apk
    archivesName.set("locked-v$appVersionName")
}

android {
    namespace = "com.locked.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.locked.app"
        minSdk = 26
        targetSdk = 34
        versionCode = appVersionCode
        versionName = appVersionName
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")

    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
