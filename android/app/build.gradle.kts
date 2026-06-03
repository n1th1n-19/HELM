plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "dev.helm.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.helm.app"
        minSdk = 29
        targetSdk = 35
        versionCode = 12
        versionName = "0.4.8"
    }

    signingConfigs {
        create("release") {
            storeFile = file(System.getenv("HELM_KEYSTORE_PATH") ?: "${System.getProperty("user.home")}/helm-release.jks")
            storePassword = System.getenv("HELM_KEYSTORE_PASS") ?: ""
            keyAlias = System.getenv("HELM_KEY_ALIAS") ?: "helm"
            keyPassword = System.getenv("HELM_KEY_PASS") ?: ""
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
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
    // Compose BOM
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.material3.window.size)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    // AndroidX
    implementation(libs.core.ktx)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Ktor WebSocket
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.websockets)

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // Image loading
    implementation(libs.coil.compose)

    // DataStore (connection settings persistence)
    implementation(libs.datastore.preferences)

    // QR code scanning
    implementation(libs.zxing.android.embedded)

    // Encrypted storage for sensitive prefs (PSK token, cert fingerprint)
    implementation(libs.security.crypto)
}
