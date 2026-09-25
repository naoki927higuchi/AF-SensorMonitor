plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// VERSION.txt (3-part Semantic Versioning) is the single version source.
val appVersion = rootProject.file("VERSION.txt").readText().trim()
val versionParts = appVersion.split('.').map { it.toInt() }
require(versionParts.size == 3) { "VERSION.txt must be MAJOR.MINOR.PATCH: $appVersion" }

// The release key lives in .local/android-signing/ (not in Git); Build-Android.ps1 passes it via environment.
val releaseKeystore = System.getenv("AFSM_KEYSTORE")
val releaseKeyPassword = System.getenv("AFSM_KEY_PASSWORD")

android {
    namespace = "com.af.sensormonitor"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.af.sensormonitor"
        minSdk = 26
        targetSdk = 36
        versionCode = versionParts[0] * 10000 + versionParts[1] * 100 + versionParts[2]
        versionName = appVersion
    }

    signingConfigs {
        if (releaseKeystore != null && releaseKeyPassword != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = releaseKeyPassword
                keyAlias = "sensormonitor"
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.reorderable)
    testImplementation(libs.junit)
}
